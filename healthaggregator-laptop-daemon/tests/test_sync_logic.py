import sqlite3
from pathlib import Path


def _setup_db(db_path: Path) -> None:
    with sqlite3.connect(db_path) as conn:
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
        conn.execute(
            """CREATE TABLE chat_conversations (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                modelId TEXT NOT NULL
            )"""
        )


def test_insert_or_ignore_dedupes_on_natural_key(tmp_path: Path) -> None:
    from sync_logic import merge_rows, MergeStrategy

    db_path = tmp_path / "test.db"
    _setup_db(db_path)

    row = {
        "sourceSystem": "EpicCleveland",
        "fhirReference": "Observation/abc",
        "testName": "Glucose",
        "status": "final",
        "importedAt": 1_700_000_000_000,
    }
    with sqlite3.connect(db_path) as conn:
        inserted1, ignored1 = merge_rows(
            conn, "lab_observations", [row], MergeStrategy.INSERT_OR_IGNORE,
            natural_key=("sourceSystem", "fhirReference"),
        )
        inserted2, ignored2 = merge_rows(
            conn, "lab_observations", [row], MergeStrategy.INSERT_OR_IGNORE,
            natural_key=("sourceSystem", "fhirReference"),
        )
    assert inserted1 == 1 and ignored1 == 0
    assert inserted2 == 0 and ignored2 == 1


def test_chat_conversations_lww_replaces_when_newer(tmp_path: Path) -> None:
    from sync_logic import merge_rows, MergeStrategy

    db_path = tmp_path / "test.db"
    _setup_db(db_path)

    old = {
        "id": "c1", "title": "Old title", "createdAt": 1_700_000_000_000,
        "updatedAt": 1_700_000_100_000, "modelId": "gpt-5",
    }
    new = {
        "id": "c1", "title": "New title", "createdAt": 1_700_000_000_000,
        "updatedAt": 1_700_000_200_000, "modelId": "gpt-5",
    }
    older_stale = {
        "id": "c1", "title": "Stale title", "createdAt": 1_700_000_000_000,
        "updatedAt": 1_700_000_050_000, "modelId": "gpt-5",
    }
    with sqlite3.connect(db_path) as conn:
        merge_rows(conn, "chat_conversations", [old], MergeStrategy.LWW_UPDATED_AT, natural_key=("id",))
        merge_rows(conn, "chat_conversations", [new], MergeStrategy.LWW_UPDATED_AT, natural_key=("id",))
        merge_rows(conn, "chat_conversations", [older_stale], MergeStrategy.LWW_UPDATED_AT, natural_key=("id",))
        (title,) = conn.execute("SELECT title FROM chat_conversations WHERE id='c1'").fetchone()
    assert title == "New title"
