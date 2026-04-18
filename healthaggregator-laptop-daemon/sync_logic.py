"""Row merge logic — called by /sync/push and internally by /sync/pull.

INSERT OR IGNORE is used for append-only tables (all FHIR + chat_messages).
LWW (last-write-wins on updatedAt) is used for chat_conversations, where the title
can be edited via the drawer rename feature on the phone.
"""
from __future__ import annotations

import sqlite3
from enum import Enum


class MergeStrategy(Enum):
    INSERT_OR_IGNORE = "insert_or_ignore"
    LWW_UPDATED_AT = "lww_updated_at"


def merge_rows(
    conn: sqlite3.Connection,
    table_name: str,
    rows: list[dict],
    strategy: MergeStrategy,
    natural_key: tuple[str, ...],
) -> tuple[int, int]:
    """Merge rows into `table_name`. Returns (inserted_count, ignored_count)."""
    if not rows:
        return 0, 0

    inserted = 0
    ignored = 0

    if strategy is MergeStrategy.INSERT_OR_IGNORE:
        for row in rows:
            # Strip auto-increment primary key (named 'id' and Int) so SQLite assigns a fresh one.
            # Tables with textual 'id' PKs (chat_messages) keep their id.
            payload = _strip_autoincrement_id(table_name, row)
            cols = list(payload.keys())
            placeholders = ",".join(["?"] * len(cols))
            col_list = ",".join(f'"{c}"' for c in cols)
            sql = f'INSERT OR IGNORE INTO "{table_name}" ({col_list}) VALUES ({placeholders})'
            cur = conn.execute(sql, tuple(payload[c] for c in cols))
            if cur.rowcount == 1:
                inserted += 1
            else:
                ignored += 1
        conn.commit()
        return inserted, ignored

    # LWW_UPDATED_AT
    for row in rows:
        key_clause = " AND ".join([f'"{k}" = ?' for k in natural_key])
        key_values = tuple(row[k] for k in natural_key)
        existing = conn.execute(
            f'SELECT updatedAt FROM "{table_name}" WHERE {key_clause}', key_values
        ).fetchone()
        if existing is None:
            payload = _strip_autoincrement_id(table_name, row)
            cols = list(payload.keys())
            placeholders = ",".join(["?"] * len(cols))
            col_list = ",".join(f'"{c}"' for c in cols)
            conn.execute(
                f'INSERT INTO "{table_name}" ({col_list}) VALUES ({placeholders})',
                tuple(payload[c] for c in cols),
            )
            inserted += 1
        else:
            if int(row["updatedAt"]) > int(existing[0]):
                update_cols = [c for c in row if c not in natural_key]
                set_clause = ",".join(f'"{c}" = ?' for c in update_cols)
                conn.execute(
                    f'UPDATE "{table_name}" SET {set_clause} WHERE {key_clause}',
                    tuple(row[c] for c in update_cols) + key_values,
                )
                inserted += 1
            else:
                ignored += 1
    conn.commit()
    return inserted, ignored


_AUTOINCREMENT_TABLES = {
    "patient_records", "lab_observations", "vitals_observations",
    "condition_records", "medication_records", "allergy_records",
    "encounter_records", "document_records", "diagnostic_report_records",
    "source_records",
}


def _strip_autoincrement_id(table_name: str, row: dict) -> dict:
    """Drop the `id` field for tables with autoincrement PKs so SQLite assigns locally."""
    if table_name not in _AUTOINCREMENT_TABLES:
        return row
    return {k: v for k, v in row.items() if k != "id"}


def natural_key_for(table_name: str) -> tuple[str, ...]:
    """Return the natural-key columns for a syncable table. See spec §'Syncable tables'."""
    return {
        "patient_records": ("sourceSystem", "fhirReference"),
        "lab_observations": ("sourceSystem", "fhirReference"),
        "vitals_observations": ("sourceSystem", "fhirReference", "componentCode"),
        "condition_records": ("sourceSystem", "fhirReference"),
        "medication_records": ("sourceSystem", "fhirReference"),
        "allergy_records": ("sourceSystem", "fhirReference"),
        "encounter_records": ("sourceSystem", "fhirReference"),
        "document_records": ("sourceSystem", "fhirReference"),
        "diagnostic_report_records": ("sourceSystem", "fhirReference"),
        "source_records": ("sourceSystem", "resourceType", "resourceId"),
        "chat_conversations": ("id",),
        "chat_messages": ("id",),
    }[table_name]


def strategy_for(table_name: str) -> MergeStrategy:
    if table_name == "chat_conversations":
        return MergeStrategy.LWW_UPDATED_AT
    return MergeStrategy.INSERT_OR_IGNORE
