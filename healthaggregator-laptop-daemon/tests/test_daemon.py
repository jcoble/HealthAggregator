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


def test_push_inserts_rows_into_db(tmp_data_dir) -> None:
    import sqlite3
    from config import Config, load_or_create_token

    cfg = Config.from_env()
    # Seed DB with the minimal schema /sync/push will merge into.
    with sqlite3.connect(cfg.db_path) as conn:
        conn.execute(
            """CREATE TABLE lab_observations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sourceSystem TEXT NOT NULL,
                fhirReference TEXT NOT NULL,
                testName TEXT NOT NULL,
                status TEXT NOT NULL,
                importedAt INTEGER NOT NULL,
                UNIQUE(sourceSystem, fhirReference)
            )"""
        )
    token = load_or_create_token(cfg.token_path)

    from daemon import make_app
    from fastapi.testclient import TestClient
    client = TestClient(make_app())

    payload = {
        "batch_id": "abc-1",
        "rows_by_table": {
            "lab_observations": [
                {
                    "id": 1,
                    "sourceSystem": "EpicCleveland",
                    "fhirReference": "Observation/1",
                    "testName": "Glucose",
                    "status": "final",
                    "importedAt": 1_700_000_000_000,
                }
            ]
        },
    }
    r = client.post(
        "/sync/push",
        json=payload,
        headers={"Authorization": f"Bearer {token}"},
    )
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["inserted_by_table"]["lab_observations"] == 1
    assert body["ignored_by_table"]["lab_observations"] == 0

    # Second push is a no-op dedupe.
    r2 = client.post(
        "/sync/push",
        json=payload,
        headers={"Authorization": f"Bearer {token}"},
    )
    body2 = r2.json()
    assert body2["inserted_by_table"]["lab_observations"] == 0
    assert body2["ignored_by_table"]["lab_observations"] == 1
