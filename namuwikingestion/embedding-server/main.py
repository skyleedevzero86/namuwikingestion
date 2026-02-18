from contextlib import asynccontextmanager

from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer

MODEL_NAME = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"

model: SentenceTransformer | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global model
    model = SentenceTransformer(MODEL_NAME)
    yield
    model = None


app = FastAPI(title="Namuwiki Embedding API", lifespan=lifespan)


class EmbedRequest(BaseModel):
    texts: list[str]


class EmbedResponse(BaseModel):
    embeddings: list[list[float]]


@app.post("/embed", response_model=EmbedResponse)
def embed(request: EmbedRequest) -> EmbedResponse:
    if not request.texts:
        return EmbedResponse(embeddings=[])
    if model is None:
        raise RuntimeError("Model not loaded")
    vectors = model.encode(request.texts, convert_to_numpy=True)
    embeddings = [v.tolist() for v in vectors]
    return EmbedResponse(embeddings=embeddings)


@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_NAME}
