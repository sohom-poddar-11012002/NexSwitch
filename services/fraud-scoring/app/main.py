import logging
import os
from contextlib import asynccontextmanager

from anthropic import Anthropic
from fastapi import FastAPI

from .cache import SemanticCache
from .db import FraudCaseDB
from .embeddings import embed
from .graph import FraudState, build_graph
from .models import ScoringRequest, ScoringResponse
from .seed import SEED_CASES

logging.basicConfig(level=logging.INFO)
log = logging.getLogger(__name__)

_state: dict = {}


@asynccontextmanager
async def lifespan(app: FastAPI):
    db_url = os.environ.get(
        "DB_URL",
        "postgresql://nexswitch_app:local_dev_password@postgres:5432/nexswitch",
    )
    api_key = os.environ.get("ANTHROPIC_API_KEY", "")
    redis_url = os.environ.get("REDIS_URL", "")
    if not api_key:
        log.warning("fraud.ANTHROPIC_API_KEY not set — LLM scoring disabled, returning default 0.10")

    db = FraudCaseDB(db_url)
    db.setup(SEED_CASES, embed)

    client = Anthropic(api_key=api_key or "placeholder")
    client.api_key = api_key  # empty string → score node falls back to default

    cache: SemanticCache | None = None
    if redis_url:
        try:
            cache = SemanticCache(redis_url)
            cache._r.ping()
            log.info("fraud.semantic_cache enabled redis_url=%s", redis_url)
        except Exception as exc:
            log.warning("fraud.semantic_cache_unavailable err=%s — caching disabled", exc)
            cache = None
    else:
        log.info("fraud.semantic_cache disabled — REDIS_URL not set")

    _state["graph"] = build_graph(db, embed, client, cache)
    log.info("fraud_scoring_service.ready")
    yield
    db.close()


app = FastAPI(title="Fraud Scoring Service", version="1.0.0", lifespan=lifespan)


@app.get("/health")
def health():
    return {"status": "ok", "llm": "configured" if os.environ.get("ANTHROPIC_API_KEY") else "disabled"}


@app.post("/score", response_model=ScoringResponse)
def score_transaction(req: ScoringRequest) -> ScoringResponse:
    # LEARN: LangGraph invoke() is synchronous — the graph runs all nodes in sequence.
    #        For production, use ainvoke() with an async FastAPI endpoint and a timeout
    #        guard so a slow Claude Haiku response doesn't stall the HTTP worker thread.
    initial: FraudState = {
        "pan_hash":     req.pan_hash,
        "amount_inr":   req.amount_inr,
        "mcc":          req.mcc,
        "network":      req.network,
        "method":       req.method,
        "hour_of_day":  req.hour_of_day,
        "features_text": "",
        "embedding":    [],
        "similar_cases": [],
        "score":        0.0,
        "reasoning":    "",
    }
    result = _state["graph"].invoke(initial)
    return ScoringResponse(score=result["score"], reasoning=result["reasoning"])
