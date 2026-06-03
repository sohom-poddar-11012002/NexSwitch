FROM python:3.12-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    libpq-dev gcc curl \
    && rm -rf /var/lib/apt/lists/*

# Install torch CPU-only first so sentence-transformers doesn't pull GPU wheels (~300MB vs ~1.5GB)
RUN pip install --no-cache-dir torch==2.5.1 --index-url https://download.pytorch.org/whl/cpu

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Pre-download embedding model during build so container starts instantly
# LEARN: Baking the model into the image trades ~90MB image size for zero cold-start download.
#        The alternative (download at runtime) breaks air-gapped or slow-network deployments.
ENV SENTENCE_TRANSFORMERS_HOME=/app/models
ENV HF_HOME=/app/models

# Pre-download bi-encoder (embedding) and cross-encoder (reranker) during build.
# LEARN: Baking models into the image trades ~170MB image size for zero cold-start download.
#        HF_HOME unifies where sentence-transformers and transformers both cache models.
RUN python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('all-MiniLM-L6-v2')"
RUN python -c "from sentence_transformers import CrossEncoder; CrossEncoder('cross-encoder/ms-marco-MiniLM-L-6-v2', max_length=512)"

COPY app/ ./app/

ENV PYTHONUNBUFFERED=1
EXPOSE 8090

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
  CMD curl -f http://localhost:8090/health || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8090"]
