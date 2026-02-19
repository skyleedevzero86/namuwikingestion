import pytest
from fastapi.testclient import TestClient


def test_health_returns_ok_and_model_name(client: TestClient):
    r = client.get("/health")
    assert r.status_code == 200
    data = r.json()
    assert data["status"] == "정상"
    assert "model" in data
    assert "paraphrase-multilingual" in data["model"] or "MiniLM" in data["model"]


def test_embed_empty_texts_returns_empty_embeddings(client: TestClient):
    r = client.post("/embed", json={"texts": []})
    assert r.status_code == 200
    assert r.json()["embeddings"] == []


def test_embed_with_text_returns_embeddings_when_model_mocked(client: TestClient, monkeypatch):
    import main as main_module

    class FakeModel:
        def encode(self, texts, convert_to_numpy=True):
            return [[0.1] * 384 for _ in texts]

    monkeypatch.setattr(main_module, "model", FakeModel())

    r = client.post("/embed", json={"texts": ["안녕 나무위키"]})
    assert r.status_code == 200
    data = r.json()
    assert "embeddings" in data
    assert len(data["embeddings"]) == 1
    assert len(data["embeddings"][0]) == 384
    assert data["embeddings"][0][0] == 0.1


def test_embed_multiple_texts_returns_same_count(client: TestClient, monkeypatch):
    import main as main_module

    class FakeModel:
        def encode(self, texts, convert_to_numpy=True):
            return [[0.2] * 384 for _ in texts]

    monkeypatch.setattr(main_module, "model", FakeModel())

    r = client.post("/embed", json={"texts": ["a", "b", "c"]})
    assert r.status_code == 200
    assert len(r.json()["embeddings"]) == 3


def test_embed_no_model_raises_500(client: TestClient, monkeypatch):
    import main as main_module
    monkeypatch.setattr(main_module, "model", None)

    r = client.post("/embed", json={"texts": ["hello"]})
    assert r.status_code == 500
