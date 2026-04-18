import sqlite3
from pathlib import Path

from tests.conftest import FIXTURES_DIR


def test_apply_migrations_bumps_user_version_and_creates_tables(tmp_path: Path) -> None:
    from schema import apply_migration

    db_path = tmp_path / "test.db"
    # Seed with an empty DB at version 1 (so 1→2 is the first applicable migration).
    with sqlite3.connect(db_path) as conn:
        # Required initial tables so ALTER TABLE in 1_to_2 has something to mutate.
        conn.execute(
            """CREATE TABLE lab_observations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sourceSystem TEXT NOT NULL,
                fhirReference TEXT NOT NULL
            )"""
        )
        conn.execute(
            """CREATE TABLE vitals_observations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sourceSystem TEXT NOT NULL,
                sourceName TEXT NOT NULL,
                fhirReference TEXT NOT NULL,
                resourceId TEXT NOT NULL,
                patientFhirId TEXT,
                loincCode TEXT,
                code TEXT NOT NULL,
                displayName TEXT NOT NULL,
                numericValue REAL,
                unit TEXT,
                componentCode TEXT,
                effectiveAt INTEGER,
                importedAt INTEGER NOT NULL
            )"""
        )
        conn.execute("PRAGMA user_version = 1")

    fixtures_dir = FIXTURES_DIR / "migrations"
    for (frm, to) in [(1, 2), (2, 3), (3, 4), (4, 5)]:
        sql = (fixtures_dir / f"{frm}_to_{to}.sql").read_text()
        apply_migration(db_path, from_version=frm, to_version=to, sql=sql)

    with sqlite3.connect(db_path) as conn:
        (version,) = conn.execute("PRAGMA user_version").fetchone()
        assert version == 5
        tables = {row[0] for row in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        assert {"lab_observations", "vitals_observations", "chat_conversations", "chat_messages"} <= tables


def test_apply_migration_rejects_version_mismatch(tmp_path: Path) -> None:
    from schema import SchemaMismatch, apply_migration

    db_path = tmp_path / "test.db"
    with sqlite3.connect(db_path) as conn:
        conn.execute("PRAGMA user_version = 3")

    try:
        apply_migration(db_path, from_version=5, to_version=6, sql="SELECT 1;")
    except SchemaMismatch as e:
        assert "expected_from=5" in str(e)
    else:
        raise AssertionError("expected SchemaMismatch")


def test_apply_migration_rolls_back_on_sql_error(tmp_path: Path) -> None:
    from schema import apply_migration

    db_path = tmp_path / "test.db"
    with sqlite3.connect(db_path) as conn:
        conn.execute("PRAGMA user_version = 1")

    try:
        apply_migration(db_path, from_version=1, to_version=2, sql="THIS IS NOT SQL;")
    except sqlite3.Error:
        pass

    with sqlite3.connect(db_path) as conn:
        (version,) = conn.execute("PRAGMA user_version").fetchone()
        assert version == 1
