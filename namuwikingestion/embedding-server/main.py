import os
from contextlib import asynccontextmanager

from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer

MODEL_NAME = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"

model: SentenceTransformer | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global model
    if os.environ.get("TESTING"):
        model = None
    else:
        model = SentenceTransformer(MODEL_NAME)
    yield
    model = None


app = FastAPI(
    title="나무위키 임베딩 API",
    description="나무위키 수집용 임베딩 서비스. POST /embed로 텍스트 임베딩, GET /health로 헬스 체크.",
    version="1.0",
    lifespan=lifespan,
)


class EmbedRequest(BaseModel):
    """임베딩할 텍스트 목록 요청 본문."""

    texts: list[str]


class EmbedResponse(BaseModel):
    """응답: 입력 텍스트당 하나의 임베딩 벡터 목록."""

    embeddings: list[list[float]]


class HealthResponse(BaseModel):
    """헬스 체크 응답."""

    status: str
    model: str


@app.post(
    "/embed",
    response_model=EmbedResponse,
    summary="텍스트 임베딩",
    description="주어진 텍스트에 대한 임베딩 벡터를 반환합니다. 빈 목록이면 빈 임베딩을 반환합니다. 모델 로드가 필요합니다.",
    responses={
        200: {"description": "임베딩 반환됨"},
        500: {"description": "모델 미로드 또는 인코딩 실패"},
    },
)
def embed(request: EmbedRequest) -> EmbedResponse:
    if not request.texts:
        return EmbedResponse(embeddings=[])
    if model is None:
        raise RuntimeError("모델이 로드되지 않았습니다")
    vectors = model.encode(request.texts, convert_to_numpy=True)
    embeddings = [v.tolist() for v in vectors]
    return EmbedResponse(embeddings=embeddings)


@app.get(
    "/health",
    response_model=HealthResponse,
    summary="헬스 체크",
    description="서비스 상태와 로드된 모델 이름을 반환합니다.",
)
def health() -> HealthResponse:
    return HealthResponse(status="정상", model=MODEL_NAME)
