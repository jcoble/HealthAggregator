"""Read rows from the laptop DB for /sync/pull. Each row is a column → value dict."""
from __future__ import annotations

import sqlite3
from pathlib import Path

SYNCABLE_TABLES = [
    "patients", "lab_observations", "vitals_observations",
    "conditions", "medications", "allergies",
    "encounters", "documents", "diagnostic_reports",
    "source_records", "chat_conversations", "chat_messages",
]


def read_all_rows(db_path: Path) -> dict[str, list[dict]]:
    """Return {table_name: [row_dict, ...]} for every syncable table. Missing tables become []."""
    out: dict[str, list[dict]] = {}
    if not db_path.exists():
        return {t: [] for t in SYNCABLE_TABLES}
    with sqlite3.connect(db_path) as conn:
        conn.row_factory = sqlite3.Row
        existing = {r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        for table in SYNCABLE_TABLES:
            if table not in existing:
                out[table] = []
                continue
            rows = [dict(r) for r in conn.execute(f'SELECT * FROM "{table}"')]
            out[table] = rows
    return out
