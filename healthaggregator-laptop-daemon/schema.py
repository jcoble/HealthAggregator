"""Schema migration: apply SQL received from the phone, bump PRAGMA user_version."""
from __future__ import annotations

import sqlite3
from pathlib import Path


class SchemaMismatch(Exception):
    """Raised when the daemon's current user_version doesn't match the phone's expectation."""


def split_statements(sql: str) -> list[str]:
    """Split `;`-separated SQL statements, dropping comments and empty segments."""
    return [s.strip() for s in sql.split(";") if s.strip()]


def current_version(db_path: Path) -> int:
    if not db_path.exists():
        return 0
    with sqlite3.connect(db_path) as conn:
        (version,) = conn.execute("PRAGMA user_version").fetchone()
        return int(version)


def apply_migration(db_path: Path, from_version: int, to_version: int, sql: str) -> None:
    """Apply migration SQL and bump user_version atomically.

    Raises SchemaMismatch if the DB is not at `from_version`.
    Rolls back on any sqlite3 error (transaction-scoped).
    """
    with sqlite3.connect(db_path) as conn:
        (actual_from,) = conn.execute("PRAGMA user_version").fetchone()
        if int(actual_from) != from_version:
            raise SchemaMismatch(
                f"expected_from={from_version} actual_from={actual_from}"
            )
        try:
            conn.execute("BEGIN")
            for stmt in split_statements(sql):
                conn.execute(stmt)
            conn.execute(f"PRAGMA user_version = {int(to_version)}")
            conn.commit()
        except sqlite3.Error:
            conn.rollback()
            raise
