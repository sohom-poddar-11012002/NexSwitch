import hashlib
import json
import logging
import time
from typing import Optional

import numpy as np
import redis

log = logging.getLogger(__name__)

# LEARN: Semantic caching — cache scoring results keyed by embedding similarity, not exact match.
#        Two transactions with slightly different feature text but cosine similarity ≥ 0.97 to a
#        cached embedding almost certainly have the same risk profile. Avoids redundant LLM calls
#        for near-duplicate transactions (e.g., same merchant, slightly different amounts).
_CACHE_INDEX_KEY = "fraud:cache:index"
_CACHE_ENTRY_PREFIX = "fraud:cache:entry:"
_SIMILARITY_THRESHOLD = 0.97
_MAX_CANDIDATES = 100
_TTL_SECONDS = 3600


def _cosine(a: list[float], b: list[float]) -> float:
    va = np.array(a, dtype=np.float32)
    vb = np.array(b, dtype=np.float32)
    denom = float(np.linalg.norm(va) * np.linalg.norm(vb))
    return float(np.dot(va, vb) / denom) if denom > 0 else 0.0


class SemanticCache:
    def __init__(self, redis_url: str):
        self._r = redis.from_url(redis_url, decode_responses=True)

    def lookup(self, embedding: list[float]) -> Optional[dict]:
        # Most-recent entries first — hits are likely clustered around recent traffic.
        entry_ids = self._r.zrange(_CACHE_INDEX_KEY, -_MAX_CANDIDATES, -1)
        for entry_id in reversed(entry_ids):
            raw = self._r.hgetall(f"{_CACHE_ENTRY_PREFIX}{entry_id}")
            if not raw:
                continue
            cached_emb = json.loads(raw["embedding"])
            sim = _cosine(embedding, cached_emb)
            if sim >= _SIMILARITY_THRESHOLD:
                log.info("fraud.cache_hit entry_id=%s similarity=%.4f", entry_id, sim)
                return {"score": float(raw["score"]), "reasoning": f"cache_hit:{raw['reasoning']}"}
        return None

    def store(self, embedding: list[float], score: float, reasoning: str) -> None:
        ts = time.time()
        entry_id = hashlib.sha256(f"{ts}:{score}:{reasoning[:20]}".encode()).hexdigest()[:16]
        pipe = self._r.pipeline()
        pipe.hset(
            f"{_CACHE_ENTRY_PREFIX}{entry_id}",
            mapping={
                "embedding": json.dumps(embedding),
                "score": str(score),
                "reasoning": reasoning,
            },
        )
        pipe.expire(f"{_CACHE_ENTRY_PREFIX}{entry_id}", _TTL_SECONDS)
        pipe.zadd(_CACHE_INDEX_KEY, {entry_id: ts})
        # Prune entries older than TTL from the sorted set index.
        pipe.zremrangebyscore(_CACHE_INDEX_KEY, "-inf", ts - _TTL_SECONDS)
        pipe.execute()
        log.info("fraud.cache_store entry_id=%s score=%.3f", entry_id, score)
