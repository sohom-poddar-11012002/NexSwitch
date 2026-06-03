import hashlib
import json
import logging
import time

import numpy as np
import psycopg2
from pgvector.psycopg2 import register_vector
from psycopg2.extras import RealDictCursor

log = logging.getLogger(__name__)

_MODEL_NAME = "all-MiniLM-L6-v2"

# LEARN: pgvector HNSW index — hierarchical navigable small world graph. Each node connects
#        to M nearest neighbours; queries traverse the graph layer-by-layer to reach the
#        approximate nearest neighbours in O(log n) time. Higher recall than IVFFlat at the
#        cost of ~2× build time and more memory. Switch from IVFFlat at ~10k+ rows.
_SCHEMA = """
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS fraud_metadata (
    key   VARCHAR(100) PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS fraud_cases (
    id            SERIAL PRIMARY KEY,
    features_text TEXT NOT NULL,
    embedding     VECTOR(384),
    amount_inr    DECIMAL(12,2),
    mcc           VARCHAR(4),
    network       VARCHAR(20),
    method        VARCHAR(20),
    is_fraud      BOOLEAN,
    pattern       VARCHAR(100),
    created_at    TIMESTAMP DEFAULT NOW()
);

ALTER TABLE fraud_cases
    ADD COLUMN IF NOT EXISTS features_tsv
    TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', features_text)) STORED;

CREATE INDEX IF NOT EXISTS fraud_cases_tsv_idx
    ON fraud_cases USING GIN (features_tsv);
"""

_INDEX_VERSION = "hnsw-v1"
_RRF_K = 60


def _compute_seed_version(seed_cases: list[dict]) -> str:
    payload = {"model": _MODEL_NAME, "seeds": seed_cases}
    digest = hashlib.sha256(
        json.dumps(payload, sort_keys=True, default=str).encode()
    ).hexdigest()[:16]
    return f"v1:{_MODEL_NAME}:{digest}"


class FraudCaseDB:
    def __init__(self, db_url: str):
        self._db_url = db_url
        self._conn: psycopg2.extensions.connection | None = None

    def _connect_with_retry(self, retries: int = 10, delay: float = 3.0) -> None:
        for attempt in range(1, retries + 1):
            try:
                self._conn = psycopg2.connect(self._db_url)
                self._conn.autocommit = True
                register_vector(self._conn)
                log.info("fraud_db.connected attempt=%d", attempt)
                return
            except Exception as exc:
                log.warning("fraud_db.connect_failed attempt=%d/%d err=%s", attempt, retries, exc)
                if attempt < retries:
                    time.sleep(delay)
        raise RuntimeError("fraud_db.connect_exhausted after %d attempts" % retries)

    def setup(self, seed_cases: list[dict], embed_fn) -> None:
        self._connect_with_retry()
        with self._conn.cursor() as cur:
            cur.execute(_SCHEMA)

            # Migrate embedding index to HNSW (idempotent: stored version in metadata).
            # LEARN: Storing migration state in a metadata table instead of re-running DDL
            #        every startup avoids dropping and rebuilding large indexes on each boot.
            cur.execute("SELECT value FROM fraud_metadata WHERE key = 'index_version'")
            row = cur.fetchone()
            if not row or row[0] != _INDEX_VERSION:
                log.info("fraud_db.migrating_index target=%s", _INDEX_VERSION)
                cur.execute("DROP INDEX IF EXISTS fraud_cases_embedding_idx")
                cur.execute(
                    """CREATE INDEX fraud_cases_embedding_idx
                           ON fraud_cases USING hnsw (embedding vector_cosine_ops)
                           WITH (m = 16, ef_construction = 64)"""
                )
                cur.execute(
                    "INSERT INTO fraud_metadata (key, value) VALUES ('index_version', %s) "
                    "ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value",
                    (_INDEX_VERSION,),
                )
                log.info("fraud_db.index_migrated version=%s", _INDEX_VERSION)

            # Seed version check — hash of (model_name + seed_data).
            # LEARN: Embedding drift: if you change the model or the features_text format,
            #        old vectors are no longer comparable to new query vectors. The seed-hash
            #        detects the mismatch and wipes the table so it re-embeds from scratch.
            version = _compute_seed_version(seed_cases)
            cur.execute("SELECT value FROM fraud_metadata WHERE key = 'seed_version'")
            row = cur.fetchone()
            if row and row[0] == version:
                log.info("fraud_db.seed_current version=%s", version)
                return
            if row:
                log.info("fraud_db.seed_stale old=%s new=%s — truncating", row[0], version)
                cur.execute("TRUNCATE fraud_cases")

            log.info("fraud_db.seeding cases=%d version=%s", len(seed_cases), version)
            for case in seed_cases:
                vec = np.array(embed_fn(case["features_text"]), dtype=np.float32)
                cur.execute(
                    """INSERT INTO fraud_cases
                           (features_text, embedding, amount_inr, mcc, network, method, is_fraud, pattern)
                       VALUES (%s, %s, %s, %s, %s, %s, %s, %s)""",
                    (
                        case["features_text"],
                        vec,
                        case["amount_inr"],
                        case["mcc"],
                        case["network"],
                        case["method"],
                        case["is_fraud"],
                        case["pattern"],
                    ),
                )
            cur.execute(
                "INSERT INTO fraud_metadata (key, value) VALUES ('seed_version', %s) "
                "ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value",
                (version,),
            )
            log.info("fraud_db.seeded cases=%d", len(seed_cases))

    def find_similar_hybrid(
        self,
        embedding: list[float],
        query_text: str,
        network: str | None = None,
        candidate_limit: int = 20,
        final_limit: int = 5,
    ) -> list[dict]:
        # LEARN: Pre-filtering — apply WHERE network = %s BEFORE the ANN index scan.
        #        pgvector evaluates the predicate at index traversal time so only matching rows
        #        are considered as neighbours. Post-filtering (ANN first, discard after) silently
        #        degrades recall when > 20% of rows are filtered — pre-filter avoids that loss.
        #        Rule: if a metadata filter removes > 20% of the table, always pre-filter.
        #
        # LEARN: Hybrid search — BM25 (keyword) catches exact matches like "MCC 7995" or
        #        "VISA"; vector search catches semantic matches like "jewelry ≈ precious metals".
        #        Reciprocal Rank Fusion (RRF): score = Σ 1/(k + rank_i). k=60 prevents a
        #        rank-1 result from dominating; no manual weight tuning needed.
        vec = np.array(embedding, dtype=np.float32)

        net_filter = "AND network = %s" if network else ""

        with self._conn.cursor(cursor_factory=RealDictCursor) as cur:
            vector_params = ([network] if network else []) + [vec, candidate_limit]
            cur.execute(
                f"""SELECT id, features_text, amount_inr, mcc, network, method, is_fraud, pattern
                   FROM fraud_cases
                   WHERE true {net_filter}
                   ORDER BY embedding <=> %s
                   LIMIT %s""",
                vector_params,
            )
            vector_rows: list[dict] = [dict(r) for r in cur.fetchall()]

            bm25_params = [query_text] + ([network] if network else []) + [query_text, candidate_limit]
            cur.execute(
                f"""SELECT id, features_text, amount_inr, mcc, network, method, is_fraud, pattern
                   FROM fraud_cases
                   WHERE features_tsv @@ plainto_tsquery('english', %s) {net_filter}
                   ORDER BY ts_rank(features_tsv, plainto_tsquery('english', %s)) DESC
                   LIMIT %s""",
                bm25_params,
            )
            bm25_rows: list[dict] = [dict(r) for r in cur.fetchall()]

        rrf_scores: dict[int, float] = {}
        rows_by_id: dict[int, dict] = {}
        penalty = candidate_limit + 1

        for rank, row in enumerate(vector_rows, start=1):
            rid = row["id"]
            rrf_scores[rid] = rrf_scores.get(rid, 0.0) + 1.0 / (_RRF_K + rank)
            rows_by_id[rid] = row

        for rank, row in enumerate(bm25_rows, start=1):
            rid = row["id"]
            rrf_scores[rid] = rrf_scores.get(rid, 0.0) + 1.0 / (_RRF_K + rank)
            rows_by_id[rid] = row

        # Any id that only appeared in one list gets a half-penalty for the missing list.
        vector_ids = {r["id"] for r in vector_rows}
        bm25_ids = {r["id"] for r in bm25_rows}
        for rid in rows_by_id:
            if rid not in vector_ids:
                rrf_scores[rid] += 1.0 / (_RRF_K + penalty)
            if rid not in bm25_ids:
                rrf_scores[rid] += 1.0 / (_RRF_K + penalty)

        sorted_ids = sorted(rrf_scores, key=lambda i: rrf_scores[i], reverse=True)
        return [rows_by_id[i] for i in sorted_ids[:final_limit]]

    def close(self) -> None:
        if self._conn and not self._conn.closed:
            self._conn.close()
