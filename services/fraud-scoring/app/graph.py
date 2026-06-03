import json
import logging
import os
from typing import Callable, Optional

from anthropic import Anthropic
from langgraph.graph import END, StateGraph
from sentence_transformers import CrossEncoder
from typing_extensions import NotRequired, TypedDict

from .cache import SemanticCache

log = logging.getLogger(__name__)

# LEARN: LangGraph StateGraph — each node is a pure function (state_in → partial_state_out).
#        The framework merges returned dicts into the shared state between nodes.
#        Graph: describe → hypothesize → retrieve → rerank → route → score.
#        route (LLM routing) and hypothesize (HyDE) keep the graph topology linear —
#        state flags (skip_llm, model_id) avoid conditional edges entirely.
class FraudState(TypedDict):
    pan_hash: str
    amount_inr: float
    mcc: str
    network: str
    method: str
    hour_of_day: int
    # populated by describe
    features_text: str
    # populated by hypothesize
    hypothesis_text: NotRequired[str]
    hypothesis_embedding: NotRequired[list]
    query_variants: NotRequired[list[str]]
    skip_llm: NotRequired[bool]
    # populated by retrieve / rerank
    embedding: list
    similar_cases: list
    # populated by route
    model_id: NotRequired[str]
    # populated by score
    score: float
    reasoning: str


_RERANKER: CrossEncoder | None = None
_MODELS_DIR = os.environ.get("SENTENCE_TRANSFORMERS_HOME", "/app/models")

# Amount threshold below which we skip the full RAG+LLM pipeline.
# LEARN: Adaptive retrieval — a trivially low-risk transaction (INR < 50, ~$0.60) doesn't
#        need Claude. Routing it to a default score saves ~200ms latency and ~$0.001 per call.
#        In production this threshold is a feature-flag; here it's a constant.
_ADAPTIVE_SKIP_AMOUNT_INR = 50.0


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


def build_graph(db, embed_fn: Callable[[str], list[float]], client: Anthropic, cache: Optional[SemanticCache] = None):
    # Pre-warm cross-encoder at startup so first request pays no model-load latency.
    reranker = _get_reranker()

    def describe(state: FraudState) -> dict:
        text = (
            f"Indian payment: INR {state['amount_inr']:.2f} via {state['network']} "
            f"{state['method']} at MCC {state['mcc']} at {state['hour_of_day']:02d}:00 IST"
        )
        return {"features_text": text}

    def hypothesize(state: FraudState) -> dict:
        # LEARN: Adaptive retrieval — skip the expensive RAG+LLM pipeline for trivially benign
        #        amounts. The skip_llm flag is checked by retrieve and score nodes so the graph
        #        still terminates cleanly without conditional edges.
        if state["amount_inr"] < _ADAPTIVE_SKIP_AMOUNT_INR:
            log.info("fraud.adaptive_skip amount_inr=%.2f", state["amount_inr"])
            return {"skip_llm": True, "hypothesis_text": "", "hypothesis_embedding": [], "query_variants": []}

        if not client.api_key:
            emb = embed_fn(state["features_text"])
            return {
                "skip_llm": False,
                "hypothesis_text": state["features_text"],
                "hypothesis_embedding": emb,
                "query_variants": [],
            }

        # LEARN: HyDE (Hypothetical Document Embeddings) — the user query ("INR 6000 at 2AM,
        #        MCC 5411") lives in the *query space*; stored fraud cases live in the *document
        #        space*. The gap between query and document embedding distributions reduces ANN
        #        recall. Generating a hypothetical matching case and embedding *that* produces a
        #        vector in the document space — much closer to actual fraud cases in the index.
        #        Cost: one Haiku call (~$0.0001) for typically +15–25% recall@5 improvement.
        #
        # LEARN: Multi-query retrieval — a single query phrasing may miss semantically relevant
        #        docs. Generating 2 variants and unioning retrieval results covers more angles
        #        without duplicating documents (dedup by ID in retrieve node).
        prompt = (
            "You are a fraud analyst for an Indian payment switch.\n"
            f"Transaction: {state['features_text']}\n\n"
            "1. Write ONE sentence describing what a matching historical fraud case in our "
            "database would look like.\n"
            "2. Provide 2 alternative phrasings of the original transaction for retrieval diversity.\n"
            "Return ONLY valid JSON (no markdown):\n"
            '{"hypothesis": "...", "variants": ["...", "..."]}'
        )
        try:
            msg = client.messages.create(
                model="claude-haiku-4-5-20251001",
                max_tokens=150,
                messages=[{"role": "user", "content": prompt}],
            )
            result = json.loads(msg.content[0].text.strip())
            hypothesis = result.get("hypothesis", state["features_text"])
            variants = result.get("variants", [])[:2]
            log.info("fraud.hypothesize hypothesis=%s variants=%d", hypothesis[:60], len(variants))
            return {
                "skip_llm": False,
                "hypothesis_text": hypothesis,
                "hypothesis_embedding": embed_fn(hypothesis),
                "query_variants": variants,
            }
        except Exception as exc:
            log.warning("fraud.hypothesize_failed err=%s — falling back to features_text", exc)
            return {
                "skip_llm": False,
                "hypothesis_text": state["features_text"],
                "hypothesis_embedding": embed_fn(state["features_text"]),
                "query_variants": [],
            }

    def retrieve(state: FraudState) -> dict:
        if state.get("skip_llm"):
            return {"embedding": [], "similar_cases": []}

        # Use HyDE hypothesis embedding for primary search (document-space vector).
        primary_emb = state.get("hypothesis_embedding") or embed_fn(state["features_text"])
        primary_text = state.get("hypothesis_text") or state["features_text"]
        network = state.get("network")

        # LEARN: Multi-query retrieval — retrieve for the hypothesis + each variant, then union
        #        results by ID. Each phrasing variant probes a different neighbourhood in the
        #        embedding space; the union surface area is larger than any single query alone.
        all_cases: dict[int, dict] = {}
        for emb, text in [(primary_emb, primary_text)] + [
            (embed_fn(v), v) for v in (state.get("query_variants") or [])
        ]:
            limit = 20 if emb is primary_emb else 10
            for c in db.find_similar_hybrid(emb, text, network=network, candidate_limit=limit, final_limit=limit):
                all_cases[c["id"]] = c

        log.info("fraud.retrieve total_unique_candidates=%d", len(all_cases))
        return {"embedding": primary_emb, "similar_cases": list(all_cases.values())}

    def rerank(state: FraudState) -> dict:
        cases = state["similar_cases"]
        if not cases:
            return {}
        query = state.get("hypothesis_text") or state["features_text"]
        pairs = [(query, c["features_text"]) for c in cases]
        scores = reranker.predict(pairs)
        ranked = sorted(zip(scores, cases), key=lambda x: x[0], reverse=True)
        return {"similar_cases": [c for _, c in ranked[:5]]}

    def route(state: FraudState) -> dict:
        # LEARN: LLM routing — use a cheap pre-signal (fraction of fraud neighbours) to pick
        #        the model tier. Haiku handles the benign majority (~15× cheaper per token);
        #        Sonnet handles high-risk bands where better chain-of-thought matters.
        #        This is industry-standard: route by complexity, not by arbitrary heuristic.
        cases = state["similar_cases"]
        if not cases or state.get("skip_llm"):
            return {"model_id": "claude-haiku-4-5-20251001"}
        fraud_fraction = sum(1 for c in cases if c["is_fraud"]) / len(cases)
        model = "claude-sonnet-4-6" if fraud_fraction >= 0.4 else "claude-haiku-4-5-20251001"
        log.info("fraud.route fraud_fraction=%.2f model=%s", fraud_fraction, model)
        return {"model_id": model}

    def score(state: FraudState) -> dict:
        # Adaptive skip — trivially benign transaction bypassed the full pipeline.
        if state.get("skip_llm"):
            return {"score": 0.03, "reasoning": "adaptive_skip_low_amount"}

        # LEARN: RAG (Retrieval-Augmented Generation) — retrieved + reranked cases act as
        #        dynamic few-shot examples. The LLM doesn't need fine-tuning; updated seed data
        #        changes behaviour immediately. This is why RAG beats static prompts for fraud:
        #        patterns change weekly, retraining a model doesn't scale to that cadence.
        if not client.api_key:
            log.warning("fraud.llm_not_configured returning default_score=0.10")
            return {"score": 0.10, "reasoning": "llm_not_configured"}

        embedding = state.get("embedding") or []
        if cache and embedding:
            hit = cache.lookup(embedding)
            if hit:
                return {"score": hit["score"], "reasoning": hit["reasoning"]}

        model_id = state.get("model_id", "claude-haiku-4-5-20251001")
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
            log.info("fraud.llm_call model=%s", model_id)
            msg = client.messages.create(
                model=model_id,
                max_tokens=100,
                messages=[{"role": "user", "content": prompt}],
            )
            raw = msg.content[0].text.strip()
            result = json.loads(raw)
            s = max(0.0, min(1.0, float(result["score"])))
            reasoning = result.get("reasoning", "")
            if cache and embedding:
                cache.store(embedding, s, reasoning)
            return {"score": s, "reasoning": reasoning}
        except Exception as exc:
            log.warning("fraud.llm_score_failed err=%s", exc)
            return {"score": 0.10, "reasoning": "llm_error"}

    g = StateGraph(FraudState)
    g.add_node("describe", describe)
    g.add_node("hypothesize", hypothesize)
    g.add_node("retrieve", retrieve)
    g.add_node("rerank", rerank)
    g.add_node("route", route)
    g.add_node("score", score)
    g.set_entry_point("describe")
    g.add_edge("describe", "hypothesize")
    g.add_edge("hypothesize", "retrieve")
    g.add_edge("retrieve", "rerank")
    g.add_edge("rerank", "route")
    g.add_edge("route", "score")
    g.add_edge("score", END)
    return g.compile()
