import os
from sentence_transformers import SentenceTransformer

# LEARN: Singleton pattern for the embedding model — loading SentenceTransformer reads ~90MB
#        from disk and initialises the neural network. Doing this once at import time means
#        every subsequent encode() call pays only inference cost (~2ms on CPU for MiniLM).
_MODEL: SentenceTransformer | None = None


def _get_model() -> SentenceTransformer:
    global _MODEL
    if _MODEL is None:
        model_dir = os.environ.get("SENTENCE_TRANSFORMERS_HOME", "/app/models")
        _MODEL = SentenceTransformer("all-MiniLM-L6-v2", cache_folder=model_dir)
    return _MODEL


def embed(text: str) -> list[float]:
    vec = _get_model().encode(text, normalize_embeddings=True)
    return vec.tolist()
