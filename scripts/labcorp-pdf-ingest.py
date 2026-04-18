#!/usr/bin/env python3
"""One-shot ingest of hand-transcribed LabCorp PDF reports.

Two PDF reports for Jesse Coble (account 09324405, patient 2968077),
both collected 2024-12-20:
  - Specimen 355-305-0696-0 (reported 2025-01-03) — big panel
  - Specimen 355-305-0744-0 (reported 2024-12-25) — heavy metals / thyroid

Usage:
  python3 scripts/labcorp-pdf-ingest.py --db ~/HealthAggregatorData/healthaggregator.db
  python3 scripts/labcorp-pdf-ingest.py --db ~/HealthAggregatorData/healthaggregator.db --execute
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import sqlite3
import sys
import time
from dataclasses import dataclass, asdict

SOURCE_SYSTEM = "labcorp"
SOURCE_NAME = "LabCorp"
PATIENT_FHIR = "Patient/2968077"


@dataclass
class RawLab:
    """A single row as it appears on a LabCorp PDF."""
    panel: str
    test: str
    result: str
    units: str | None
    ref: str | None
    flag: str | None = None  # 'H', 'L', etc.


# ---- Specimen 1: 355-305-0696-0, Date Collected 2024-12-20 08:18 ET, Reported 2025-01-03 ----
# Original PDF: /Users/blackcolours/Downloads/13d8f649-0d2a-4e92-be53-e9444ed40c0f.pdf

SPEC1_ID = "355-305-0696-0"
SPEC1_COLLECTED = dt.datetime(2024, 12, 20, 8, 18, tzinfo=dt.timezone(-dt.timedelta(hours=5)))  # ET
SPEC1_SOURCE_PDF = "13d8f649-0d2a-4e92-be53-e9444ed40c0f.pdf"

SPEC1_LABS: list[RawLab] = [
    # CBC With Differential/Platelet
    RawLab("CBC With Differential/Platelet", "WBC",                         "5.8",  "x10E3/uL", "3.4-10.8"),
    RawLab("CBC With Differential/Platelet", "RBC",                         "5.52", "x10E6/uL", "4.14-5.80"),
    RawLab("CBC With Differential/Platelet", "Hemoglobin",                  "17.4", "g/dL",     "13.0-17.7"),
    RawLab("CBC With Differential/Platelet", "Hematocrit",                  "52.3", "%",        "37.5-51.0", "H"),
    RawLab("CBC With Differential/Platelet", "MCV",                         "95",   "fL",       "79-97"),
    RawLab("CBC With Differential/Platelet", "MCH",                         "31.5", "pg",       "26.6-33.0"),
    RawLab("CBC With Differential/Platelet", "MCHC",                        "33.3", "g/dL",     "31.5-35.7"),
    RawLab("CBC With Differential/Platelet", "RDW",                         "12.5", "%",        "11.6-15.4"),
    RawLab("CBC With Differential/Platelet", "Platelets",                   "224",  "x10E3/uL", "150-450"),
    RawLab("CBC With Differential/Platelet", "Neutrophils",                 "68",   "%",        "Not Estab."),
    RawLab("CBC With Differential/Platelet", "Lymphs",                      "19",   "%",        "Not Estab."),
    RawLab("CBC With Differential/Platelet", "Monocytes",                   "10",   "%",        "Not Estab."),
    RawLab("CBC With Differential/Platelet", "Eos",                         "2",    "%",        "Not Estab."),
    RawLab("CBC With Differential/Platelet", "Basos",                       "1",    "%",        "Not Estab."),
    RawLab("CBC With Differential/Platelet", "Neutrophils (Absolute)",      "3.9",  "x10E3/uL", "1.4-7.0"),
    RawLab("CBC With Differential/Platelet", "Lymphs (Absolute)",           "1.1",  "x10E3/uL", "0.7-3.1"),
    RawLab("CBC With Differential/Platelet", "Monocytes(Absolute)",         "0.6",  "x10E3/uL", "0.1-0.9"),
    RawLab("CBC With Differential/Platelet", "Eos (Absolute)",              "0.1",  "x10E3/uL", "0.0-0.4"),
    RawLab("CBC With Differential/Platelet", "Baso (Absolute)",             "0.0",  "x10E3/uL", "0.0-0.2"),
    RawLab("CBC With Differential/Platelet", "Immature Granulocytes",       "0",    "%",        "Not Estab."),
    RawLab("CBC With Differential/Platelet", "Immature Grans (Abs)",        "0.0",  "x10E3/uL", "0.0-0.1"),

    # Comp. Metabolic Panel (14)
    RawLab("Comp. Metabolic Panel (14)",     "Glucose",                     "93",   "mg/dL",    "70-99"),
    RawLab("Comp. Metabolic Panel (14)",     "BUN",                         "14",   "mg/dL",    "6-24"),
    RawLab("Comp. Metabolic Panel (14)",     "Creatinine",                  "0.97", "mg/dL",    "0.76-1.27"),
    RawLab("Comp. Metabolic Panel (14)",     "eGFR",                        "101",  "mL/min/1.73", ">59"),
    RawLab("Comp. Metabolic Panel (14)",     "BUN/Creatinine Ratio",        "14",   None,       "9-20"),
    RawLab("Comp. Metabolic Panel (14)",     "Sodium",                      "142",  "mmol/L",   "134-144"),
    RawLab("Comp. Metabolic Panel (14)",     "Potassium",                   "3.4",  "mmol/L",   "3.5-5.2", "L"),
    RawLab("Comp. Metabolic Panel (14)",     "Chloride",                    "101",  "mmol/L",   "96-106"),
    RawLab("Comp. Metabolic Panel (14)",     "Carbon Dioxide, Total",       "27",   "mmol/L",   "20-29"),
    RawLab("Comp. Metabolic Panel (14)",     "Calcium",                     "9.4",  "mg/dL",    "8.7-10.2"),
    RawLab("Comp. Metabolic Panel (14)",     "Protein, Total",              "6.9",  "g/dL",     "6.0-8.5"),
    RawLab("Comp. Metabolic Panel (14)",     "Albumin",                     "4.6",  "g/dL",     "4.1-5.1"),
    RawLab("Comp. Metabolic Panel (14)",     "Globulin, Total",             "2.3",  "g/dL",     "1.5-4.5"),
    RawLab("Comp. Metabolic Panel (14)",     "Bilirubin, Total",            "0.5",  "mg/dL",    "0.0-1.2"),
    RawLab("Comp. Metabolic Panel (14)",     "Alkaline Phosphatase",        "58",   "IU/L",     "44-121"),
    RawLab("Comp. Metabolic Panel (14)",     "AST (SGOT)",                  "28",   "IU/L",     "0-40"),
    RawLab("Comp. Metabolic Panel (14)",     "ALT (SGPT)",                  "21",   "IU/L",     "0-44"),

    # HLA DRB1,3,4,5,DQB1 — allele strings, stored as textValue.
    RawLab("HLA DRB1,3,4,5,DQB1 (IR)", "DRB1 Allele 1",    "DRB1*11:04:01G", None, None),
    RawLab("HLA DRB1,3,4,5,DQB1 (IR)", "DRB1 Allele 2",    "DRB1*15:01:01G", None, None),
    RawLab("HLA DRB1,3,4,5,DQB1 (IR)", "DRB3 Allele 1",    "DRB3*02:02:01G", None, None),
    RawLab("HLA DRB1,3,4,5,DQB1 (IR)", "DRB3 Allele 2",    "DRB3*-",         None, None),
    RawLab("HLA DRB1,3,4,5,DQB1 (IR)", "DRB4 Allele 1",    "DRB4*-",         None, None),
    RawLab("HLA DRB1,3,4,5,DQB1 (IR)", "DRB4 Allele 2",    "DRB4*-",         None, None),
    RawLab("HLA DRB1,3,4,5,DQB1 (IR)", "DRB5 Allele 1",    "DRB5*01:01:01G", None, None),
    RawLab("HLA DRB1,3,4,5,DQB1 (IR)", "DRB5 Allele 2",    "DRB5*-",         None, None),
    RawLab("HLA DRB1,3,4,5,DQB1 (IR)", "DQB1 Allele 1",    "DQB1*03:01:01G", None, None),
    RawLab("HLA DRB1,3,4,5,DQB1 (IR)", "DQB1 Allele 2",    "DQB1*06:02:01G", None, None),

    # Estradiol, Free Serum
    RawLab("Estradiol, Free Serum", "Estradiol, Serum, MS",   "22",   "pg/mL",  "Adult Males: 8.0 - 35"),
    RawLab("Estradiol, Free Serum", "Free Estradiol, Percent","2.2",  "%",      "Adult Males: 1.7 - 5.4"),
    RawLab("Estradiol, Free Serum", "Free Estradiol, Serum",  "0.48", "pg/mL",  "Adult Males: 0.2 - 1.5"),

    # von Willebrand Profile
    RawLab("von Willebrand Profile", "Factor VIII Activity",              "124", "%", "56-140"),
    RawLab("von Willebrand Profile", "von Willebrand Factor (vWF) Ag",    "190", "%", "50-200"),
    RawLab("von Willebrand Profile", "vWF Activity",                      "109", "%", "50-200"),

    # Folate, RBC
    RawLab("Folate, RBC", "Folate, Hemolysate",  "472.0", "ng/mL", "Not Estab."),
    RawLab("Folate, RBC", "Folate, RBC",         "902",   "ng/mL", ">498"),

    # Testosterone, Free and Total
    RawLab("Testosterone, Free and Total", "Testosterone",               "386", "ng/dL", "264-916"),
    RawLab("Testosterone, Free and Total", "Free Testosterone (Direct)", "4.6", "pg/mL", "6.8-21.5", "L"),

    # Pregnenolone, MS
    RawLab("Pregnenolone, MS", "Pregnenolone, MS", "<10", "ng/dL", "Adults: <151"),

    # SARS-CoV-2 Semi-Quant Spike Ab
    RawLab("SARS-CoV-2 Semi-Quant Spike Ab", "SARS-CoV-2 Spike Ab Dilution", "18550", "U/mL", "Negative<0.8"),
    RawLab("SARS-CoV-2 Semi-Quant Spike Ab", "SARS-CoV-2 Spike Ab Interp",   "Positive", None, None),

    # DHEA-Sulfate, Serum
    RawLab("DHEA-Sulfate, Serum", "DHEA-Sulfate, LCMS", "32", "ug/dL", "Adult Males (41 - 50y): 16 - 390"),

    # ACTH, Plasma
    RawLab("ACTH, Plasma", "ACTH, Plasma", "3.0", "pg/mL", "7.2-63.3", "L"),

    # Total Glutathione
    RawLab("Total Glutathione", "Total Glutathione", "313", "ug/mL", "176-323"),

    # VIP, Plasma
    RawLab("VIP, Plasma", "VIP, Plasma", "22.2", "pg/mL", "0.0-58.8"),

    # ADH
    RawLab("ADH", "ADH", "<0.8", "pg/mL", "0.0-4.7"),

    # Zinc, RBC
    RawLab("Zinc, RBC", "Zinc, RBC", "1579", "ug/dL", "878-1660"),

    # Vitamin D, 25-Hydroxy
    RawLab("Vitamin D, 25-Hydroxy", "Vitamin D, 25-Hydroxy", "33.2", "ng/mL", "30.0-100.0"),

    # Leptin, Serum  (reference is a full BMI table — store as text)
    RawLab("Leptin, Serum", "Leptin, Serum", "12.9", "ng/mL", "Male Ranges by BMI (see report)"),

    # MMP-9
    RawLab("MMP-9 (Matrix metalloprot.-9)", "MMP9", "656", "ng/mL", "<984"),

    # GGT
    RawLab("GGT", "GGT", "18", "IU/L", "0-65"),

    # Iron
    RawLab("Iron", "Iron", "95", "ug/dL", "38-169"),

    # Vitamin B12
    RawLab("Vitamin B12", "Vitamin B12", "531", "pg/mL", "232-1245"),

    # Osmolality
    RawLab("Osmolality", "Osmolality", "288", "mOsmol/kg", "275-295"),

    # Ferritin
    RawLab("Ferritin", "Ferritin", "50", "ng/mL", "30-400"),

    # Melanocyte Stimulating Hormone
    RawLab("Melanocyte Stimulating Hormone", "Melanocyte Stimulating Hormone", "22", "pg/mL", "0-40"),

    # Magnesium, RBC
    RawLab("Magnesium, RBC", "Magnesium, RBC", "5.7", "mg/dL", "3.7-7.0"),

    # Selenium, Serum/Plasma
    RawLab("Selenium, Serum/Plasma", "Selenium, Serum/Plasma", "131", "ug/L", "93-198"),
]

# ---- Specimen 2: 355-305-0744-0, Date Collected 2024-12-20 11:36 ET, Reported 2024-12-25 ----
# Original PDF: /Users/blackcolours/Downloads/cc6725ab-7dc9-474f-a352-4cebc5f5963c.pdf

SPEC2_ID = "355-305-0744-0"
SPEC2_COLLECTED = dt.datetime(2024, 12, 20, 11, 36, tzinfo=dt.timezone(-dt.timedelta(hours=5)))
SPEC2_SOURCE_PDF = "cc6725ab-7dc9-474f-a352-4cebc5f5963c.pdf"

SPEC2_LABS: list[RawLab] = [
    RawLab("Heavy Metals Profile II, Blood", "Lead, Blood",    "<1.0", "ug/dL", "0.0-3.4"),
    RawLab("Heavy Metals Profile II, Blood", "Arsenic, Blood", "1",    "ug/L",  "0-9"),
    RawLab("Heavy Metals Profile II, Blood", "Mercury, Blood", "1.2",  "ug/L",  "0.0-14.9"),
    RawLab("Heavy Metals Profile II, Blood", "Cadmium, Blood", "<0.5", "ug/L",  "0.0-1.2"),

    RawLab("Thyroxine (T4) Free, Direct",   "T4, Free (Direct)",        "1.73", "ng/dL", "0.82-1.77"),
    RawLab("Homocyst(e)ine",                "Homocyst(e)ine",           "9.9",  "umol/L", "0.0-14.5"),
    RawLab("Uric Acid",                     "Uric Acid",                "4.5",  "mg/dL",  "3.8-8.4"),
    RawLab("Thyroid Peroxidase (TPO) Ab",   "Thyroid Peroxidase (TPO) Ab","12", "IU/mL",  "0-34"),
    RawLab("Triiodothyronine (T3), Free",   "Triiodothyronine (T3), Free","3.0","pg/mL",  "2.0-4.4"),
]


def parse_value(result: str) -> tuple[float | None, str | None]:
    """Return (numeric_value, text_value).
    If result parses cleanly as a float, numeric=float, text=None.
    Otherwise numeric=None, text=result (e.g. '<1.0', '<10', 'Positive', allele strings).
    """
    s = (result or "").strip()
    try:
        return float(s), None
    except ValueError:
        return None, s


def parse_ref_range(ref: str | None) -> tuple[float | None, float | None, str | None]:
    """Return (low, high, text). Simple numeric range like '3.5-5.2' → (3.5, 5.2, None).
    Anything else → (None, None, original).
    Small special case: leading 'Adult Males: X - Y' normalizes to (X, Y, original text).
    """
    if not ref:
        return None, None, None
    text = ref.strip()
    m = re.match(r"^\s*(-?\d+(?:\.\d+)?)\s*-\s*(-?\d+(?:\.\d+)?)\s*$", text)
    if m:
        return float(m.group(1)), float(m.group(2)), None
    m2 = re.search(r"(-?\d+(?:\.\d+)?)\s*-\s*(-?\d+(?:\.\d+)?)", text)
    if m2:
        return float(m2.group(1)), float(m2.group(2)), text
    return None, None, text


def slugify(name: str) -> str:
    """Reduce a test name to a stable slug for use inside a composite ID."""
    s = name.lower()
    s = re.sub(r"[^a-z0-9]+", "-", s)
    return s.strip("-")


def map_interpretation(flag: str | None) -> str | None:
    if not flag:
        return None
    return flag.upper()  # 'H' or 'L' as-is; matches HL7 v2.0078


def build_rows(
    specimen_id: str,
    collected: dt.datetime,
    pdf_name: str,
    labs: list[RawLab],
    now_ms: int,
):
    effective_ms = int(collected.timestamp() * 1000)
    lab_rows = []
    src_rows = []

    # DiagnosticReport source_record (one per specimen)
    dr_raw = {
        "resourceType": "DiagnosticReport",
        "id": specimen_id,
        "specimenId": specimen_id,
        "account": "09324405",
        "patient": PATIENT_FHIR,
        "effectiveDateTime": collected.isoformat(),
        "sourcePdf": pdf_name,
        "entries": [asdict(r) for r in labs],
    }
    src_rows.append({
        "resourceType": "DiagnosticReport",
        "resourceId": specimen_id,
        "fhirReference": f"DiagnosticReport/{specimen_id}",
        "rawJson": json.dumps(dr_raw, separators=(",", ":")),
    })

    for r in labs:
        num, text = parse_value(r.result)
        low, high, ref_text = parse_ref_range(r.ref)
        slug = slugify(r.test)
        obs_id = f"{specimen_id}#{slug}"

        panel_slug = slugify(r.panel)
        # Lab row. canonicalPanelName left NULL so RecordsRepository.ensureNamesNormalized
        # on the phone fills it with a Title Case form via LabNameNormalizer, matching the
        # convention used by Cleveland / Summa rows from the Health Connect pipeline.
        lab_rows.append({
            "sourceSystem": SOURCE_SYSTEM,
            "sourceName": SOURCE_NAME,
            "fhirReference": f"Observation/{obs_id}",
            "resourceId": obs_id,
            "patientFhirId": PATIENT_FHIR,
            "diagnosticReportReference": f"DiagnosticReport/{specimen_id}",
            "loincCode": None,
            "testName": r.test,
            "numericValue": num,
            "textValue": text,
            "unit": r.units,
            "referenceLow": low,
            "referenceHigh": high,
            "referenceText": ref_text,
            "interpretation": map_interpretation(r.flag),
            "effectiveAt": effective_ms,
            "status": "final",
            "importedAt": now_ms,
            "canonicalTestName": slug or None,
            "canonicalPanelName": None,
            "serviceRequestReference": f"DiagnosticReport/{specimen_id}#{panel_slug}" if panel_slug else None,
            "serviceRequestDisplay": r.panel,
        })

        # Observation source_record (mirror)
        obs_raw = {
            "resourceType": "Observation",
            "id": obs_id,
            "panel": r.panel,
            "test": r.test,
            "result": r.result,
            "units": r.units,
            "referenceRange": r.ref,
            "flag": r.flag,
            "effectiveDateTime": collected.isoformat(),
            "sourcePdf": pdf_name,
            "specimenId": specimen_id,
        }
        src_rows.append({
            "resourceType": "Observation",
            "resourceId": obs_id,
            "fhirReference": f"Observation/{obs_id}",
            "rawJson": json.dumps(obs_raw, separators=(",", ":")),
        })

    return lab_rows, src_rows


LAB_INSERT = """
INSERT OR IGNORE INTO lab_observations
  (sourceSystem, sourceName, fhirReference, resourceId,
   patientFhirId, diagnosticReportReference, loincCode,
   testName, numericValue, textValue, unit,
   referenceLow, referenceHigh, referenceText,
   interpretation, effectiveAt, status, importedAt,
   canonicalTestName, canonicalPanelName,
   serviceRequestReference, serviceRequestDisplay)
VALUES (:sourceSystem, :sourceName, :fhirReference, :resourceId,
        :patientFhirId, :diagnosticReportReference, :loincCode,
        :testName, :numericValue, :textValue, :unit,
        :referenceLow, :referenceHigh, :referenceText,
        :interpretation, :effectiveAt, :status, :importedAt,
        :canonicalTestName, :canonicalPanelName,
        :serviceRequestReference, :serviceRequestDisplay)
"""

SRC_INSERT = """
INSERT OR IGNORE INTO source_records
  (syncJobId, sourceSystem, sourceName, resourceType,
   resourceId, fhirReference, rawJson, importedAt)
VALUES (NULL, :sourceSystem, :sourceName, :resourceType,
        :resourceId, :fhirReference, :rawJson, :importedAt)
"""


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--db", required=True)
    p.add_argument("--execute", action="store_true")
    args = p.parse_args()

    db_path = os.path.expanduser(args.db)
    if not os.path.isfile(db_path):
        print(f"ERROR: DB not found: {db_path}", file=sys.stderr)
        return 2

    now_ms = int(time.time() * 1000)

    lab_rows_1, src_rows_1 = build_rows(SPEC1_ID, SPEC1_COLLECTED, SPEC1_SOURCE_PDF, SPEC1_LABS, now_ms)
    lab_rows_2, src_rows_2 = build_rows(SPEC2_ID, SPEC2_COLLECTED, SPEC2_SOURCE_PDF, SPEC2_LABS, now_ms)
    lab_rows = lab_rows_1 + lab_rows_2
    src_rows = src_rows_1 + src_rows_2

    print(f"Parsed {len(lab_rows)} lab rows across 2 specimens.")
    print(f"Parsed {len(src_rows)} source_records (2 DiagnosticReport + {len(lab_rows)} Observation).")

    # Show a few samples
    print("\nSample rows:")
    for r in (lab_rows[0], lab_rows[3], lab_rows[30] if len(lab_rows) > 30 else lab_rows[-1], lab_rows[-1]):
        print(f"  [{r['canonicalPanelName'][:30]:<30}] {r['testName']:<30} "
              f"num={r['numericValue']} text={r['textValue']!r} "
              f"unit={r['unit']!r} range=({r['referenceLow']},{r['referenceHigh']}) "
              f"flag={r['interpretation']}")

    if not args.execute:
        print("\nDRY RUN — no changes written. Re-run with --execute to apply.")
        return 0

    con = sqlite3.connect(db_path)
    try:
        con.execute("BEGIN")
        lab_inserted = 0
        for r in lab_rows:
            cur = con.execute(LAB_INSERT, r)
            if cur.rowcount > 0:
                lab_inserted += 1
        src_inserted = 0
        for r in src_rows:
            payload = {
                "sourceSystem": SOURCE_SYSTEM,
                "sourceName": SOURCE_NAME,
                "resourceType": r["resourceType"],
                "resourceId": r["resourceId"],
                "fhirReference": r["fhirReference"],
                "rawJson": r["rawJson"],
                "importedAt": now_ms,
            }
            cur = con.execute(SRC_INSERT, payload)
            if cur.rowcount > 0:
                src_inserted += 1
        con.execute("COMMIT")
    except Exception:
        con.execute("ROLLBACK")
        raise
    finally:
        con.close()

    print(f"\n  lab_observations: +{lab_inserted} inserted ({len(lab_rows) - lab_inserted} already present)")
    print(f"  source_records:   +{src_inserted} inserted ({len(src_rows) - src_inserted} already present)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
