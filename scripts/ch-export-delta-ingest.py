#!/usr/bin/env python3
"""Ingest the delta between two CommonHealth exports.

Given an OLD export and a NEW export directory, ingest every resource that
appears in NEW but not in OLD. Each resource is attributed to a sourceSystem
slug derived from its performer (or inherited from its parent DiagnosticReport).

Designed for incremental import as new organizations are added to CommonHealth.
Dedup is by (sourceSystem, fhirReference) via INSERT OR IGNORE.

Usage:
  python3 scripts/ch-export-delta-ingest.py \
      --old /Users/blackcolours/Downloads/health-records \
      --new "/Users/blackcolours/Downloads/health-records 2" \
      --db ~/HealthAggregatorData/healthaggregator.db          # dry-run

  python3 scripts/ch-export-delta-ingest.py \
      --old ... --new ... --db ... --execute
"""
from __future__ import annotations

import argparse
import datetime as dt
import glob
import json
import os
import re
import sqlite3
import sys
import time
from collections import Counter, defaultdict
from dataclasses import dataclass


# Map performer display names we've seen to (sourceSystem, sourceName).
PERFORMER_MAP: dict[str, tuple[str, str]] = {
    "quest diagnostics": ("quest-diagnostics", "Quest Diagnostics"),
    "labcorp": ("labcorp", "LabCorp"),
    "laboratory corporation": ("labcorp", "LabCorp"),
    "cleveland clinic": ("cleveland-clinic", "Cleveland Clinic"),
    "summa health": ("summa-health", "Summa Health"),
    "mayo clinic": ("mayo-clinic", "Mayo Clinic"),
}


def attrib_from_performer(perf: dict | None) -> tuple[str, str] | None:
    if not perf:
        return None
    if isinstance(perf, list):
        perf = perf[0] if perf else {}
    display = (perf.get("display") or "").strip().lower()
    if display in PERFORMER_MAP:
        return PERFORMER_MAP[display]
    ref = (perf.get("reference") or "").lower()
    for needle, attrib in PERFORMER_MAP.items():
        if needle in display or needle.replace(" ", "") in ref or needle in ref:
            return attrib
    return None


def iso_to_epoch_ms(iso: str | None) -> int | None:
    if not iso:
        return None
    try:
        d = dt.datetime.fromisoformat(iso.replace("Z", "+00:00"))
        return int(d.timestamp() * 1000)
    except ValueError:
        return None


def loinc_of(codings: list[dict]) -> str | None:
    for c in codings or []:
        if (c.get("system") or "").endswith("loinc.org"):
            return c.get("code")
    return None


def primary_display(codings: list[dict], fallback: str | None = None) -> str:
    for c in codings or []:
        if (c.get("system") or "").endswith("loinc.org") and c.get("display"):
            return c["display"]
    for c in codings or []:
        if c.get("display"):
            return c["display"]
    return fallback or "Unknown"


def interpretation_of(obs: dict) -> str | None:
    interp = obs.get("interpretation")
    if isinstance(interp, list):
        interp = interp[0] if interp else {}
    elif not isinstance(interp, dict):
        interp = {}
    for c in (interp.get("coding") or []):
        if c.get("code"):
            return c["code"]
    return None


def is_lab_observation(obs: dict) -> bool:
    cat = obs.get("category")
    if isinstance(cat, list):
        items = cat
    elif isinstance(cat, dict):
        items = [cat]
    else:
        return False
    for item in items:
        for c in (item.get("coding") or []):
            if (c.get("code") or "").lower() == "laboratory":
                return True
    return False


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
    serviceRequestReference: str | None
    serviceRequestDisplay: str | None

    def tuple(self):
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

    def tuple(self):
        return (
            None, self.sourceSystem, self.sourceName, self.resourceType,
            self.resourceId, self.fhirReference, self.rawJson, self.importedAt,
        )


def observation_to_lab(
    obs: dict,
    attrib: tuple[str, str],
    patient_ref: str | None,
    dr_ref: str | None,
    panel_display: str | None,
    now_ms: int,
) -> LabRow:
    ss, sn = attrib
    codings = (obs.get("code") or {}).get("coding") or []
    test_name = primary_display(codings, (obs.get("code") or {}).get("text"))
    loinc = loinc_of(codings)
    value_qty = obs.get("valueQuantity") or {}
    numeric = value_qty.get("value")
    unit = value_qty.get("unit")
    text_value = obs.get("valueString") or obs.get("valueCodeableConcept", {}).get("text")
    ref_range = (obs.get("referenceRange") or [{}])[0]
    ref_low = (ref_range.get("low") or {}).get("value")
    ref_high = (ref_range.get("high") or {}).get("value")
    ref_text = ref_range.get("text")
    effective = iso_to_epoch_ms(obs.get("effectiveDateTime") or obs.get("issued"))
    obs_id = obs["id"]

    return LabRow(
        sourceSystem=ss,
        sourceName=sn,
        fhirReference=f"Observation/{obs_id}",
        resourceId=obs_id,
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


def collect(
    old_dir: str,
    new_dir: str,
    now_ms: int,
) -> tuple[list[LabRow], list[SourceRow], Counter]:
    old_files = set(os.path.basename(f) for f in os.listdir(old_dir))
    new_paths = [os.path.join(new_dir, f) for f in os.listdir(new_dir) if f not in old_files]

    # Index NEW observations by their resource.id so we can resolve DR.result references.
    obs_by_id: dict[str, tuple[str, dict]] = {}
    for path in new_paths:
        base = os.path.basename(path)
        if not base.startswith("Observation-"):
            continue
        try:
            d = json.load(open(path))
        except (json.JSONDecodeError, OSError):
            continue
        rid = d.get("id")
        if rid:
            obs_by_id[rid] = (path, d)

    lab_rows: list[LabRow] = []
    src_rows: list[SourceRow] = []
    attribution_counter: Counter = Counter()
    # Track which observations were visited via a DR (so we don't double-ingest).
    touched_obs_ids: set[str] = set()

    # Pass 1: DiagnosticReports (and the Observations they point at)
    for path in new_paths:
        base = os.path.basename(path)
        if not base.startswith("DiagnosticReport-"):
            continue
        try:
            dr = json.load(open(path))
        except (json.JSONDecodeError, OSError):
            continue

        attrib = attrib_from_performer(dr.get("performer"))
        if attrib is None:
            attribution_counter[("(unknown DR performer)", str(dr.get("performer")))] += 1
            continue
        ss, sn = attrib

        dr_id = dr.get("id")
        if not dr_id:
            continue
        dr_ref = f"DiagnosticReport/{dr_id}"
        patient_ref = (dr.get("subject") or {}).get("reference")
        panel_display = (dr.get("code") or {}).get("text") or primary_display((dr.get("code") or {}).get("coding") or [])

        # Store DR in source_records
        src_rows.append(SourceRow(
            sourceSystem=ss, sourceName=sn,
            resourceType="DiagnosticReport", resourceId=dr_id,
            fhirReference=dr_ref,
            rawJson=json.dumps(dr, separators=(",", ":")),
            importedAt=now_ms,
        ))
        attribution_counter[(ss, "DiagnosticReport")] += 1

        # Resolve each result reference → local observation file.
        for result in (dr.get("result") or []):
            ref = result.get("reference", "")
            obs_id_candidate = ref.rsplit("/", 1)[-1]
            hit = obs_by_id.get(obs_id_candidate)
            if hit is None:
                attribution_counter[(ss, "unresolvable result ref")] += 1
                continue
            obs_path, obs = hit
            if obs["id"] in touched_obs_ids:
                continue
            touched_obs_ids.add(obs["id"])
            if not is_lab_observation(obs):
                attribution_counter[(ss, "non-lab observation, skipped")] += 1
                continue
            lab_rows.append(observation_to_lab(obs, attrib, patient_ref, dr_ref, panel_display, now_ms))
            src_rows.append(SourceRow(
                sourceSystem=ss, sourceName=sn,
                resourceType="Observation", resourceId=obs["id"],
                fhirReference=f"Observation/{obs['id']}",
                rawJson=json.dumps(obs, separators=(",", ":")),
                importedAt=now_ms,
            ))
            attribution_counter[(ss, "lab observation")] += 1

    # Pass 2: Standalone new Observations not touched via any DR — attribute by their own performer.
    for obs_id, (obs_path, obs) in obs_by_id.items():
        if obs_id in touched_obs_ids:
            continue
        if not is_lab_observation(obs):
            attribution_counter[("(skip)", "non-lab standalone observation")] += 1
            continue
        attrib = attrib_from_performer(obs.get("performer"))
        if attrib is None:
            attribution_counter[("(unknown obs performer)", str(obs.get("performer")))] += 1
            continue
        patient_ref = (obs.get("subject") or {}).get("reference")
        lab_rows.append(observation_to_lab(obs, attrib, patient_ref, None, None, now_ms))
        src_rows.append(SourceRow(
            sourceSystem=attrib[0], sourceName=attrib[1],
            resourceType="Observation", resourceId=obs["id"],
            fhirReference=f"Observation/{obs['id']}",
            rawJson=json.dumps(obs, separators=(",", ":")),
            importedAt=now_ms,
        ))
        attribution_counter[(attrib[0], "standalone lab observation")] += 1

    return lab_rows, src_rows, attribution_counter


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


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--old", required=True)
    p.add_argument("--new", required=True)
    p.add_argument("--db", required=True)
    p.add_argument("--execute", action="store_true")
    args = p.parse_args()

    old_dir = os.path.expanduser(args.old)
    new_dir = os.path.expanduser(args.new)
    db_path = os.path.expanduser(args.db)

    if not os.path.isdir(old_dir) or not os.path.isdir(new_dir) or not os.path.isfile(db_path):
        print("ERROR: bad paths", file=sys.stderr)
        return 2

    now_ms = int(time.time() * 1000)
    lab_rows, src_rows, ac = collect(old_dir, new_dir, now_ms)
    print(f"Parsed {len(lab_rows)} lab rows + {len(src_rows)} source_records (DRs + observations).\n")
    print("Attribution breakdown:")
    for k, c in ac.most_common():
        print(f"  {c:5d}  {k}")

    if lab_rows:
        r = lab_rows[0]
        print(f"\nSample lab row:")
        print(f"  {r.sourceSystem} / {r.sourceName}")
        print(f"  fhirReference = {r.fhirReference}")
        print(f"  testName      = {r.testName}  (loinc={r.loincCode})")
        print(f"  value         = {r.numericValue} {r.unit!r} (text={r.textValue!r})")
        print(f"  range         = {r.referenceLow} - {r.referenceHigh}")
        print(f"  effectiveAt   = {r.effectiveAt}  (patient={r.patientFhirId})")
        print(f"  panel         = {r.serviceRequestDisplay!r}  (sr={r.serviceRequestReference})")

    if not args.execute:
        print("\nDRY RUN — no changes written. Re-run with --execute to apply.")
        return 0

    con = sqlite3.connect(db_path)
    try:
        con.execute("BEGIN")
        lab_n = 0
        for r in lab_rows:
            cur = con.execute(LAB_INSERT, r.tuple())
            if cur.rowcount > 0:
                lab_n += 1
        src_n = 0
        for r in src_rows:
            cur = con.execute(SRC_INSERT, r.tuple())
            if cur.rowcount > 0:
                src_n += 1
        con.execute("COMMIT")
    except Exception:
        con.execute("ROLLBACK")
        raise
    finally:
        con.close()

    print(f"\n  lab_observations: +{lab_n} inserted ({len(lab_rows) - lab_n} already present)")
    print(f"  source_records:   +{src_n} inserted ({len(src_rows) - src_n} already present)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
