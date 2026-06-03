import json
import logging
import os
from typing import Callable

from anthropic import Anthropic
from langgraph.graph import END, StateGraph
from sentence_transformers import CrossEncoder
from typing_extensions import TypedDict

log = logging.getLogger(__name__)

# LEARN: LangGraph StateGraph — each node is a pure function (state_in → partial_state_out).
#        The framework merges returned dicts into the shared state between nodes.
#        This graph: describe → retrieve → rerank → score.
#        The rerank node sits between ANN retrieval and LLM scoring; it narrows 20 candidates
#        to 5 using a cross-encoder, keeping the LLM context window lean and precise.
class FraudState(TypedDict):
    pan_hash: str
    amount_inr: float
    mcc: str
    network: str
    method: str
    hour_of_day: int
    # populated by nodes
    features_text: str
    embedding: list
    similar_cases: list
    score: float
    reasoning: str


_RERANKER: CrossEncoder | None = None
_MODELS_DIR = os.environ.get("SENTENCE_TRANSFORMERS_HOME", "/app/models")


def _get_reranker() -> CrossEncoder:
    # LEARN: Cross-encoder reranking — bi-encoder (retrieval) embeds query and document
    #        independently for O(1) ANN lookup. Cross-encoder feeds (query, document) together
    #        as one sequence classification input — much higher accuracy but O(n) per candidate.
    #        Pattern: ANN top-20 → cross-encoder top-5 → inject into LLM prompt.
    global _RERANKER
    if _RERANKER is None:
        os.environ.setdefault("HF_HOME", _MODELS_DIR)
        _RERANKER = CrossEncoder("cross-encoder/ms-marco-MiniLM-L-6-v2", max_length=512)
        log.info("fraud.reranker_loaded model=ms-marco-MiniLM-L-6-v2")
    return _RERANKER


def build_graph(db, embed_fn: Callable[[str], list[float]], client: Anthropic):
    # Pre-warm cross-encoder at startup so first request pays no model-load latency.
    reranker = _get_reranker()

    def describe(state: FraudState) -> dict:
        text = (
            f"Indian payment: INR {state['amount_inr']:.2f} via {state['network']} "
            f"{state['method']} at MCC {state['mcc']} at {state['hour_of_day']:02d}:00 IST"
        )
        return {"features_text": text}

    def retrieve(state: FraudState) -> dict:
        # LEARN: Hybrid search — BM25 catches exact token matches ("MCC 7995", "VISA");
        #        vector search catches semantic matches ("jewelry ≈ precious metals at midnight").
        #        RRF merges both ranked lists without needing hand-tuned weights.
        #        Retrieves top-20 candidates for the rerank node to narrow to 5.
        embedding = embed_fn(state["features_text"])
        cases = db.find_similar_hybrid(
            embedding, state["features_text"], candidate_limit=20, final_limit=20
        )
        return {"embedding": embedding, "similar_cases": cases}

    def rerank(state: FraudState) -> dict:
        cases = state["similar_cases"]
        if not cases:
            return {}
        query = state["features_text"]
        pairs = [(query, c["features_text"]) for c in cases]
        scores = reranker.predict(pairs)
        ranked = sorted(zip(scores, cases), key=lambda x: x[0], reverse=True)
        return {"similar_cases": [c for _, c in ranked[:5]]}

    def score(state: FraudState) -> dict:
        # LEARN: RAG (Retrieval-Augmented Generation) — retrieved + reranked cases act as
        #        dynamic few-shot examples. The LLM doesn't need fine-tuning; updated seed data
        #        changes behaviour immediately. This is why RAG beats static prompts for fraud:
        #        patterns change weekly, retraining a model doesn't scale to that cadence.
        if not client.api_key:
            log.warning("fraud.llm_not_configured returning default_score=0.10")
            return {"score": 0.10, "reasoning": "llm_not_configured"}

        cases_text = "\n".join(
            f"- INR {c['amount_inr']}, MCC {c['mcc']}, {c['network']} {c['method']}, "
            f"fraud={c['is_fraud']}, pattern={c['pattern']}"
            for c in state["similar_cases"]
        ) or "No similar cases found."

        prompt = (
            "You are a fraud detection model for an Indian payment switch.\n\n"
            f"Transaction to score:\n{state['features_text']}\n\n"
            f"Similar past cases from the fraud database (reranked by relevance):\n{cases_text}\n\n"
            "Based on the transaction details and historical patterns, estimate the fraud probability.\n"
            "Return ONLY valid JSON with no markdown:\n"
            '{"score": 0.0, "reasoning": "one sentence explanation"}'
        )

        try:
            msg = client.messages.create(
                model="claude-haiku-4-5-20251001",
                max_tokens=100,
                messages=[{"role": "user", "content": prompt}],
            )
            raw = msg.content[0].text.strip()
            result = json.loads(raw)
            s = max(0.0, min(1.0, float(result["score"])))
            return {"score": s, "reasoning": result.get("reasoning", "")}
        except Exception as exc:
            log.warning("fraud.llm_score_failed err=%s", exc)
            return {"score": 0.10, "reasoning": "llm_error"}

    g = StateGraph(FraudState)
    g.add_node("describe", describe)
    g.add_node("retrieve", retrieve)
    g.add_node("rerank", rerank)
    g.add_node("score", score)
    g.set_entry_point("describe")
    g.add_edge("describe", "retrieve")
    g.add_edge("retrieve", "rerank")
    g.add_edge("rerank", "score")
    g.add_edge("score", END)
    return g.compile()
