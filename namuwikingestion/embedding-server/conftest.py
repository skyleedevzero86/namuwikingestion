import os

import pytest
from fastapi.testclient import TestClient

os.environ["TESTING"] = "1"


@pytest.fixture
def client():
    from main import app
    with TestClient(app) as c:
        yield c
