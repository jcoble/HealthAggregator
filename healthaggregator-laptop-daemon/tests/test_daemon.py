from pathlib import Path

from fastapi.testclient import TestClient


def _make_client(tmp_data_dir: Path) -> TestClient:
    from daemon import make_app
    app = make_app()
    return TestClient(app)


def test_health_returns_ok(tmp_data_dir: Path) -> None:
    client = _make_client(tmp_data_dir)
    response = client.get("/sync/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"


def test_version_requires_auth(tmp_data_dir: Path) -> None:
    client = _make_client(tmp_data_dir)
    response = client.get("/sync/version")
    assert response.status_code == 401


def test_version_with_valid_token_returns_payload(tmp_data_dir: Path) -> None:
    from config import Config, load_or_create_token
    cfg = Config.from_env()
    token = load_or_create_token(cfg.token_path)
    client = _make_client(tmp_data_dir)
    response = client.get("/sync/version", headers={"Authorization": f"Bearer {token}"})
    assert response.status_code == 200
    body = response.json()
    assert "schema_version" in body
    assert body["daemon_version"] == "0.1.0"
