import os
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.responses import HTMLResponse, RedirectResponse
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
    docs_url=None,
    redoc_url=None,
)

SPRING_APP_URL = os.environ.get("SPRING_APP_URL", "http://localhost:8081")
UNIFIED_DOCS_URL = f"{SPRING_APP_URL}/api-docs-unified.html"


@app.get("/", response_class=HTMLResponse, include_in_schema=False)
def api_docs_entry():
    return f"""<!DOCTYPE html>
<html lang="ko">
<head><meta charset="UTF-8"><title>API 문서</title></head>
<body>
  <h1>나무위키 API 문서</h1>
  <ul>
    <li><a href="{UNIFIED_DOCS_URL}">통합 API 문서 (수집 + 임베딩)</a></li>
  </ul>
</body>
</html>"""


@app.get("/docs", response_class=RedirectResponse, include_in_schema=False)
def redirect_docs_to_unified():
    return RedirectResponse(url=UNIFIED_DOCS_URL)


@app.get("/api-docs-unified", response_class=RedirectResponse, include_in_schema=False)
def redirect_to_unified_docs():
    return RedirectResponse(url=UNIFIED_DOCS_URL)


class EmbedRequest(BaseModel):
    texts: list[str]


class EmbedResponse(BaseModel):
    embeddings: list[list[float]]


class HealthResponse(BaseModel):
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


