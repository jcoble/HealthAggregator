#!/usr/bin/env python3
"""One-shot ingest of LabCorp lab data from a CommonHealth FHIR export.

Source: per-resource JSON files in a directory (CommonHealth export format).
Target: lab_observations + source_records in the laptop Room DB.

Default: DRY RUN (prints what would be inserted). Pass --execute to actually write.

  python3 scripts/labcorp-ingest.py \
      --export-dir ~/Downloads/health-records \
      --db ~/HealthAggregatorData/healthaggregator.db        # dry-run

  python3 scripts/labcorp-ingest.py \
      --export-dir ~/Downloads/health-records \
      --db ~/HealthAggregatorData/healthaggregator.db --execute
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import sqlite3
import sys
import time
from dataclasses import dataclass
from typing import Any, Iterable

SOURCE_SYSTEM = "labcorp"
SOURCE_NAME = "LabCorp"


def is_labcorp(resource: dict) -> bool:
    """Heuristic: does this FHIR resource belong to LabCorp?"""
    blob = json.dumps(resource).lower()
    return "labcorp.com" in blob or "labcorp" in blob


def iso_to_epoch_ms(iso: str | None) -> int | None:
    """2021-02-12T09:15:00-05:00 → epoch millis."""
    if not iso:
        return None
    import datetime as dt
    try:
        d = dt.datetime.fromisoformat(iso.replace("Z", "+00:00"))
        return int(d.timestamp() * 1000)
    except ValueError:
        return None


def loinc_of(coding_list: list[dict]) -> str | None:
    for c in coding_list or []:
        if (c.get("system") or "").endswith("loinc.org"):
            return c.get("code")
    return None


def primary_display(coding_list: list[dict], fallback_text: str | None = None) -> str:
    """Best human-readable test name from a coding list. Prefer LOINC, fall back to first, then text."""
    for c in coding_list or []:
        if (c.get("system") or "").endswith("loinc.org") and c.get("display"):
            return c["display"]
    for c in coding_list or []:
        if c.get("display"):
            return c["display"]
    return fallback_text or "Unknown"


def interpretation_of(obs: dict) -> str | None:
    interp = obs.get("interpretation") or {}
    if isinstance(interp, list):
        interp = interp[0] if interp else {}
    for c in (interp.get("coding") or []):
        if c.get("code"):
            return c["code"]
    return None


@dataclass
class LabRow:
    sourceSystem: str
    sourceName: str
    fhirReference: str
    resourceId: str
    patientFhirId: str | None
    diagnosticReportReference: str | None
    loincCode: str | None
    testName: str
    numericValue: float | None
    textValue: str | None
    unit: str | None
    referenceLow: float | None
    referenceHigh: float | None
    referenceText: str | None
    interpretation: str | None
    effectiveAt: int | None
    status: str
    importedAt: int
    canonicalTestName: str | None
    serviceRequestReference: str | None = None
    serviceRequestDisplay: str | None = None

    def as_insert_tuple(self) -> tuple:
        return (
            self.sourceSystem, self.sourceName, self.fhirReference, self.resourceId,
            self.patientFhirId, self.diagnosticReportReference, self.loincCode,
            self.testName, self.numericValue, self.textValue, self.unit,
            self.referenceLow, self.referenceHigh, self.referenceText,
            self.interpretation, self.effectiveAt, self.status, self.importedAt,
            self.canonicalTestName,
            self.serviceRequestReference, self.serviceRequestDisplay,
        )


@dataclass
class SourceRow:
    sourceSystem: str
    sourceName: str
    resourceType: str
    resourceId: str
    fhirReference: str
    rawJson: str
    importedAt: int

    def as_insert_tuple(self) -> tuple:
        # syncJobId=NULL
        return (
            None, self.sourceSystem, self.sourceName, self.resourceType,
            self.resourceId, self.fhirReference, self.rawJson, self.importedAt,
        )


def build_lab_row_from_observation(
    obs: dict,
    report: dict | None,
    resource_id: str,
    patient_ref: str | None,
    now_ms: int,
) -> LabRow:
    codings = (obs.get("code") or {}).get("coding") or []
    test_name = primary_display(codings, (obs.get("code") or {}).get("text"))
    loinc = loinc_of(codings)

    # Panel display: prefer the DiagnosticReport's code.text / primary display.
    panel_display: str | None = None
    if report:
        rpt_code = report.get("code") or {}
        panel_display = rpt_code.get("text") or primary_display(rpt_code.get("coding") or [])

    value_qty = obs.get("valueQuantity") or {}
    numeric = value_qty.get("value")
    unit = value_qty.get("unit")
    text_value = obs.get("valueString")

    ref_range = (obs.get("referenceRange") or [{}])[0]
    ref_low = (ref_range.get("low") or {}).get("value")
    ref_high = (ref_range.get("high") or {}).get("value")
    ref_text = ref_range.get("text")

    effective = iso_to_epoch_ms(obs.get("effectiveDateTime") or obs.get("issued"))

    dr_ref = f"DiagnosticReport/{report['id']}" if report else None

    return LabRow(
        sourceSystem=SOURCE_SYSTEM,
        sourceName=SOURCE_NAME,
        fhirReference=f"Observation/{resource_id}",
        resourceId=resource_id,
        patientFhirId=patient_ref,
        diagnosticReportReference=dr_ref,
        loincCode=loinc,
        testName=test_name,
        numericValue=float(numeric) if isinstance(numeric, (int, float)) else None,
        textValue=text_value,
        unit=unit,
        referenceLow=float(ref_low) if isinstance(ref_low, (int, float)) else None,
        referenceHigh=float(ref_high) if isinstance(ref_high, (int, float)) else None,
        referenceText=ref_text,
        interpretation=interpretation_of(obs),
        effectiveAt=effective,
        status=obs.get("status") or "final",
        importedAt=now_ms,
        canonicalTestName=(test_name or "").strip().lower() or None,
        serviceRequestReference=dr_ref,
        serviceRequestDisplay=panel_display,
    )


def collect_rows(export_dir: str, now_ms: int) -> tuple[list[LabRow], list[SourceRow]]:
    lab_rows: list[LabRow] = []
    src_rows: list[SourceRow] = []

    for path in sorted(glob.glob(os.path.join(export_dir, "DiagnosticReport-*.json"))):
        with open(path) as f:
            report = json.load(f)
        if not is_labcorp(report):
            continue

        report_id = report.get("id")
        if not report_id:
            print(f"WARN: DiagnosticReport missing id: {path}", file=sys.stderr)
            continue

        patient_ref = (report.get("subject") or {}).get("reference")

        # 1. Record the whole DiagnosticReport in source_records (one entry per report).
        src_rows.append(SourceRow(
            sourceSystem=SOURCE_SYSTEM,
            sourceName=SOURCE_NAME,
            resourceType="DiagnosticReport",
            resourceId=report_id,
            fhirReference=f"DiagnosticReport/{report_id}",
            rawJson=json.dumps(report, separators=(",", ":")),
            importedAt=now_ms,
        ))

        # 2. For each contained lab Observation, create one lab_observations row
        #    AND one source_records row (so Observations are queryable both ways).
        for contained in (report.get("contained") or []):
            if contained.get("resourceType") != "Observation":
                continue
            cats = (contained.get("category") or {})
            if isinstance(cats, list):
                cats = cats[0] if cats else {}
            category_codes = [c.get("code") for c in (cats.get("coding") or [])]
            if "laboratory" not in category_codes:
                continue

            local_id = contained.get("id") or "anon"
            resource_id = f"{report_id}#{local_id}"  # stable composite key

            lab_rows.append(build_lab_row_from_observation(
                contained, report, resource_id, patient_ref, now_ms
            ))
            src_rows.append(SourceRow(
                sourceSystem=SOURCE_SYSTEM,
                sourceName=SOURCE_NAME,
                resourceType="Observation",
                resourceId=resource_id,
                fhirReference=f"Observation/{resource_id}",
                rawJson=json.dumps(contained, separators=(",", ":")),
                importedAt=now_ms,
            ))

    # 3. Standalone LabCorp Observation files (rare — 3 of them).
    for path in sorted(glob.glob(os.path.join(export_dir, "Observation-*.json"))):
        with open(path) as f:
            obs = json.load(f)
        if not is_labcorp(obs):
            continue
        cats = obs.get("category") or {}
        if isinstance(cats, list):
            cats = cats[0] if cats else {}
        category_codes = [c.get("code") for c in (cats.get("coding") or [])]
        if "laboratory" not in category_codes:
            continue

        resource_id = obs.get("id")
        if not resource_id:
            continue

        patient_ref = (obs.get("subject") or {}).get("reference")
        lab_rows.append(build_lab_row_from_observation(
            obs, None, resource_id, patient_ref, now_ms
        ))
        src_rows.append(SourceRow(
            sourceSystem=SOURCE_SYSTEM,
            sourceName=SOURCE_NAME,
            resourceType="Observation",
            resourceId=resource_id,
            fhirReference=f"Observation/{resource_id}",
            rawJson=json.dumps(obs, separators=(",", ":")),
            importedAt=now_ms,
        ))

    return lab_rows, src_rows


LAB_INSERT = """
INSERT OR IGNORE INTO lab_observations
  (sourceSystem, sourceName, fhirReference, resourceId,
   patientFhirId, diagnosticReportReference, loincCode,
   testName, numericValue, textValue, unit,
   referenceLow, referenceHigh, referenceText,
   interpretation, effectiveAt, status, importedAt,
   canonicalTestName,
   serviceRequestReference, serviceRequestDisplay)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
"""

SRC_INSERT = """
INSERT OR IGNORE INTO source_records
  (syncJobId, sourceSystem, sourceName, resourceType,
   resourceId, fhirReference, rawJson, importedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?)
"""


def execute_ingest(db_path: str, lab_rows: list[LabRow], src_rows: list[SourceRow]) -> tuple[int, int]:
    con = sqlite3.connect(db_path)
    try:
        con.execute("BEGIN")
        lab_inserted = 0
        for r in lab_rows:
            cur = con.execute(LAB_INSERT, r.as_insert_tuple())
            if cur.rowcount > 0:
                lab_inserted += 1
        src_inserted = 0
        for r in src_rows:
            cur = con.execute(SRC_INSERT, r.as_insert_tuple())
            if cur.rowcount > 0:
                src_inserted += 1
        con.execute("COMMIT")
    except Exception:
        con.execute("ROLLBACK")
        raise
    finally:
        con.close()
    return lab_inserted, src_inserted


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--export-dir", required=True)
    p.add_argument("--db", required=True)
    p.add_argument("--execute", action="store_true", help="Actually write to the DB (default is dry-run)")
    args = p.parse_args()

    export_dir = os.path.expanduser(args.export_dir)
    db_path = os.path.expanduser(args.db)

    if not os.path.isdir(export_dir):
        print(f"ERROR: export dir not found: {export_dir}", file=sys.stderr)
        return 2
    if not os.path.isfile(db_path):
        print(f"ERROR: DB not found: {db_path}", file=sys.stderr)
        return 2

    now_ms = int(time.time() * 1000)
    lab_rows, src_rows = collect_rows(export_dir, now_ms)

    print(f"Parsed {len(lab_rows)} lab rows, {len(src_rows)} source_records.")
    if lab_rows:
        r = lab_rows[0]
        print("Sample lab row:")
        print(f"  fhirReference = {r.fhirReference}")
        print(f"  testName      = {r.testName!r}")
        print(f"  loincCode     = {r.loincCode}")
        print(f"  value         = {r.numericValue} {r.unit} (text={r.textValue!r})")
        print(f"  range         = {r.referenceLow} - {r.referenceHigh} ({r.referenceText!r})")
        print(f"  interpretation= {r.interpretation}")
        print(f"  effectiveAt   = {r.effectiveAt}  (patient={r.patientFhirId})")

    if not args.execute:
        print("\nDRY RUN — no changes written. Re-run with --execute to apply.")
        return 0

    print(f"\nExecuting ingest against {db_path}…")
    lab_n, src_n = execute_ingest(db_path, lab_rows, src_rows)
    print(f"  lab_observations: +{lab_n} inserted ({len(lab_rows) - lab_n} already present)")
    print(f"  source_records:   +{src_n} inserted ({len(src_rows) - src_n} already present)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
