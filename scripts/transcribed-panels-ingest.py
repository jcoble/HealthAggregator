#!/usr/bin/env python3
"""Ingest hand-transcribed lab panels from scanned PDFs (Goldfarb, Mayo).

Goldfarb PDFs come from Quest Diagnostics (2013) and Accu Reference Medical
Lab (2014-2017). Mayo PDFs are all 2020 from Mayo Clinic.

Skipped: Qadir folders (confirmed 100% overlap with existing Cleveland Clinic
data from the Health Connect sync path — see check run at DB state).

Usage (dry-run default; pass --execute to write):
  python3 scripts/transcribed-panels-ingest.py --db ~/HealthAggregatorData/healthaggregator.db
  python3 scripts/transcribed-panels-ingest.py --db ... --execute
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
from dataclasses import dataclass


# ---- Source-system slug mapping ----
QUEST = ("quest-diagnostics", "Quest Diagnostics")
ACCUREF = ("accureference", "Accu Reference Medical Lab")
MAYO = ("mayo-clinic", "Mayo Clinic")
MILLENNIUM = ("millennium-health", "Millennium Health PGT")
IGENEX = ("igenex", "IGeneX (AcuDart)")


# ---- Panel data structures ----
@dataclass
class Lab:
    test: str
    result: str
    units: str | None = None
    ref: str | None = None
    flag: str | None = None  # 'H' / 'L' / 'HH' / 'LL' / 'A'


@dataclass
class Panel:
    source: tuple[str, str]
    patient_ref: str
    specimen_id: str  # stable id unique per report
    panel_name: str
    collected: dt.datetime  # use ET by convention
    source_pdf: str
    labs: list[Lab]


ET = dt.timezone(-dt.timedelta(hours=5))


# ---- Transcribed data ----
PANELS: list[Panel] = []

# ======================================================================
# Goldfarb / Quest Diagnostics — 2013-08-20 — AT535795M (the 40-test report)
# Source PDF: Medical Records/Dr. Goldfarb/Coble_8-20-13.2 labs.pdf
# ======================================================================
PANELS.append(Panel(
    source=QUEST, patient_ref="Patient/qd-272906245",
    specimen_id="AT535795M",
    panel_name="Comprehensive Metabolic Panel w/eGFR",
    collected=dt.datetime(2013, 8, 20, 15, 19, tzinfo=ET),
    source_pdf="Medical Records/Dr. Goldfarb/Coble_8-20-13.2 labs.pdf",
    labs=[
        Lab("Sodium", "140", "mmol/L", "135-146"),
        Lab("Potassium", "4.1", "mmol/L", "3.5-5.3"),
        Lab("Chloride", "107", "mmol/L", "98-110"),
        Lab("Carbon Dioxide", "24", "mmol/L", "19-30"),
        Lab("Calcium", "9.1", "mg/dL", "8.6-10.3"),
        Lab("Alkaline Phosphatase", "54", "U/L", "40-115"),
        Lab("AST", "48", "U/L", "10-40", "H"),
        Lab("ALT", "29", "U/L", "9-46"),
        Lab("Bilirubin, Total", "0.5", "mg/dL", "0.2-1.2"),
        Lab("Glucose", "96", "mg/dL", "65-99"),
        Lab("Urea Nitrogen (BUN)", "20", "mg/dL", "7-25"),
        Lab("Creatinine", "1.06", "mg/dL", "0.60-1.35"),
        Lab("BUN/Creatinine Ratio", "18.5", None, "6-22"),
        Lab("Protein, Total", "7.0", "g/dL", "6.1-8.1"),
        Lab("Albumin", "4.5", "g/dL", "3.6-5.1"),
        Lab("Globulin, Calculated", "2.5", "g/dL", "1.9-3.7"),
        Lab("A/G Ratio", "1.7", None, "1.0-2.5"),
        Lab("eGFR Non-Afr. American", "94", "mL/min/1.73", ">=60"),
        Lab("eGFR African American", "109", "mL/min/1.73", ">=60"),
    ],
))
PANELS.append(Panel(
    source=QUEST, patient_ref="Patient/qd-272906245",
    specimen_id="AT535795M",
    panel_name="CBC W/ Diff and PLT",
    collected=dt.datetime(2013, 8, 20, 15, 19, tzinfo=ET),
    source_pdf="Medical Records/Dr. Goldfarb/Coble_8-20-13.2 labs.pdf",
    labs=[
        Lab("WBC", "5.6", "THOUS/MCL", "3.8-10.8"),
        Lab("RBC", "4.82", "MILL/MCL", "4.20-5.80"),
        Lab("Hemoglobin", "15.1", "g/dL", "13.2-17.1"),
        Lab("Hematocrit", "44.4", "%", "38.5-50.0"),
        Lab("MCV", "92.0", "fL", "80.0-100.0"),
        Lab("MCH", "31.4", "pg", "27.0-33.0"),
        Lab("MCHC", "34.1", "g/dL", "32.0-36.0"),
        Lab("RDW", "13.7", "%", "11.0-15.0"),
        Lab("Platelet Count", "177", "THOUS/MCL", "140-400"),
        Lab("Neutrophils, Absolute", "3280", "CELLS/MCL", "1500-7800"),
        Lab("Lymphocytes, Absolute", "1720", "CELLS/MCL", "850-3900"),
        Lab("Monocytes, Absolute", "490", "CELLS/MCL", "200-950"),
        Lab("Eosinophils, Absolute", "130", "CELLS/MCL", "15-500"),
        Lab("Basophils, Absolute", "20", "CELLS/MCL", "0-200"),
        Lab("Total Neutrophils %", "58", "%", "38-80"),
        Lab("Total Lymphocytes %", "30", "%", "15-49"),
        Lab("Monocytes %", "9", "%", "0-13"),
        Lab("Eosinophils %", "2", "%", "0-8"),
        Lab("Basophils %", "0", "%", "0-2"),
    ],
))
PANELS.append(Panel(
    source=QUEST, patient_ref="Patient/qd-272906245",
    specimen_id="AT535795M",
    panel_name="Additional Tests (Quest 2013-08-20)",
    collected=dt.datetime(2013, 8, 20, 15, 19, tzinfo=ET),
    source_pdf="Medical Records/Dr. Goldfarb/Coble_8-20-13.2 labs.pdf",
    labs=[
        Lab("Iron, Total", "70", "mcg/dL", "45-170"),
        Lab("TIBC", "309", "mcg/dL", "250-425"),
        Lab("% Saturation", "23", "%", "20-50"),
        Lab("Hemoglobin A1c", "5.4", "%", "0.0-5.6"),
        Lab("Estradiol", "44", "pg/mL", "<=39", "H"),
        Lab("Ferritin", "60", "ng/mL", "20-345"),
        Lab("TSH", "0.48", "mIU/L", "0.40-4.50"),
        Lab("T4, Free", "1.3", "ng/dL", "0.8-1.8"),
        Lab("T3, Free", "3.1", "pg/mL", "2.3-4.2"),
        Lab("DHEA Sulfate", "159", "ug/dL", "110-370"),
        Lab("Thyroglobulin Antibodies", "<20", "IU/mL", "<20"),
        Lab("Thyroid Peroxidase AB", "<10", "IU/mL", "<35"),
        Lab("Magnesium, RBC", "4.7", "mg/dL", "4.0-6.4"),
        Lab("IGF-I, LC/MS", "248", "ng/mL", "53-331"),
        Lab("IGF-I Z-Score (Male)", "1.1", "SD", "-2.0 to +2.0"),
        Lab("Dihydrotestosterone", "40", "ng/dL", "16-79"),
        Lab("T3, Reverse, LC/MS/MS", "18", "ng/dL", "8-25"),
        Lab("Vitamin D, 25-OH, Total", "35", "ng/mL", "30-100"),
        Lab("Vitamin D, 25-OH, D3", "35", "ng/mL", None),
        Lab("Vitamin D, 25-OH, D2", "<4", "ng/mL", None),
        Lab("Pregnenolone, LC/MS/MS", "<5", "ng/dL", "13-208", "L"),
        Lab("Testosterone, Total, LC/MS/MS", "596", "ng/dL", "250-1100"),
        Lab("Testosterone, Free", "116.2", "pg/mL", "35.0-155.0"),
        Lab("Cortisol, Total", "18.7", "mcg/dL", "4.6-20.6"),
        Lab("Cortisol, Free, LC/MS/MS", "1.06", "mcg/dL", "0.07-0.93", "H"),
    ],
))

# ======================================================================
# Goldfarb / Accureference — 2014-05-22
# Source PDF: Medical Records/Dr. Goldfarb/Coble_5-22-14.pdf
# ======================================================================
PANELS.append(Panel(
    source=ACCUREF, patient_ref="Patient/ar-A1405221213",
    specimen_id="AR-1405221213",
    panel_name="CBC + Automated Diff",
    collected=dt.datetime(2014, 5, 22, 0, 0, tzinfo=ET),
    source_pdf="Medical Records/Dr. Goldfarb/Coble_5-22-14.pdf",
    labs=[
        Lab("WBC", "5.0", "10^3/uL", "4.0-10.5"),
        Lab("RBC", "4.84", "10^6/uL", "4.7-6.0"),
        Lab("Hemoglobin", "15.1", "g/dL", "13.5-18.0"),
        Lab("Hematocrit", "46", "%", "42-52"),
        Lab("MCV", "96", "fL", "78-100"),
        Lab("MCH", "31.2", "pg", "27.0-31.0", "H"),
        Lab("MCHC", "32.7", "g/dL", "32.0-36.0"),
        Lab("RDW", "12.7", "%", "11.5-14.0"),
        Lab("Platelets", "184", "10^3/uL", "150-450"),
        Lab("MPV", "10.42", "fL", None),
        Lab("Segmented %", "54.5", "%", "42-74"),
        Lab("Segmented #", "2.7", "10^3/uL", "1.70-6.70"),
        Lab("Lymphocytes %", "31.64", "%", "18.00-45.00"),
        Lab("Lymphocytes #", "1.6", "10^3/uL", "0.90-4.50"),
        Lab("Monocytes %", "11.5", "%", "4.50-12.30"),
        Lab("Monocytes #", "0.6", "10^3/uL", "0.20-0.80"),
        Lab("Eosinophils %", "1.67", "%", "0.70-8.10"),
        Lab("Eosinophils #", "0.08", "10^3/uL", "0.0-0.50"),
        Lab("Basophils %", "0.67", "%", "0.00-3.00"),
        Lab("Basophils #", "0.03", "10^3/uL", "0.00-0.20"),
    ],
))
PANELS.append(Panel(
    source=ACCUREF, patient_ref="Patient/ar-A1405221213",
    specimen_id="AR-1405221213",
    panel_name="Comprehensive Metabolic Panel",
    collected=dt.datetime(2014, 5, 22, 0, 0, tzinfo=ET),
    source_pdf="Medical Records/Dr. Goldfarb/Coble_5-22-14.pdf",
    labs=[
        Lab("Sodium", "139", "mEq/L", "136-145"),
        Lab("Potassium", "4.1", "mEq/L", "3.5-5.1"),
        Lab("Chloride", "102", "mEq/L", "98-107"),
        Lab("Alkaline Phosphatase", "52", "U/L", "34-104"),
        Lab("Glucose", "74", "mg/dL", "70-105"),
        Lab("BUN", "21", "mg/dL", "7-25"),
        Lab("Creatinine, Serum", "1.08", "mg/dL", "0.60-1.30"),
        Lab("BUN/Creatinine Ratio", "19", None, "8-28"),
        Lab("Bilirubin, Total", "0.5", "mg/dL", "0.3-1.0"),
        Lab("Calcium", "9.2", "mg/dL", "8.6-10.5"),
        Lab("Protein, Total", "7.2", "g/dL", "6.4-8.9"),
        Lab("Albumin", "4.8", "g/dL", "3.5-5.7"),
        Lab("ALT (SGPT)", "27", "U/L", "7-52"),
        Lab("AST (SGOT)", "39", "U/L", "13-39"),
        Lab("Carbon Dioxide", "29", "mEq/L", "21-32"),
        Lab("Globulin", "2.4", "g/dL", "1.8-4.0"),
        Lab("A/G Ratio", "2.0", None, "0.8-2.7"),
        Lab("eGFR", ">60", "mL/min/1.73", ">60"),
    ],
))
PANELS.append(Panel(
    source=ACCUREF, patient_ref="Patient/ar-A1405221213",
    specimen_id="AR-1405221213",
    panel_name="Additional Tests (Accureference 2014-05-22)",
    collected=dt.datetime(2014, 5, 22, 0, 0, tzinfo=ET),
    source_pdf="Medical Records/Dr. Goldfarb/Coble_5-22-14.pdf",
    labs=[
        Lab("Iron", "65", "ug/dL", "50-212"),
        Lab("TIBC", "333", "ug/dL", "205-512"),
        Lab("UIBC", "268", "ug/dL", "155-300"),
        Lab("Saturation %", "20", "%", "20-55"),
        Lab("Testosterone, Total", "284.00", "ng/dL", "175.0-781.0"),
        Lab("Testosterone, Free (Calculated)", "28.00", "%", "24.3-110.2"),
        Lab("Sex Hormone Binding Globulin", "35", "nmol/L", "13.2-89.6"),
        Lab("Ferritin", "42.8", "ng/mL", "28-365"),
        Lab("TSH3-Ultra", "0.832", "uIU/mL", "0.550-4.780"),
        Lab("IGF-I", "246", "ng/mL", "83-246"),
        Lab("Estradiol", "44.0", "pg/mL", "0.0-39.8", "H"),
        Lab("Free T4", "0.91", "ng/dL", "0.61-1.12"),
        Lab("Free T3", "3.7", "pg/mL", "2.3-4.2"),
        Lab("DHEA-Sulfate", "23", "ug/dL", "34.5-568.9", "L"),
        Lab("Reverse T3, LC-MS/MS", "14.2", "ng/dL", "9.0-27.0"),
        Lab("Vitamin D, 25-Hydroxy", "44.1", "ng/mL", "30-100"),
        Lab("Aldosterone", "<3.00", "ng/dL", "<3.0-39.2"),
        Lab("Pregnenolone", "6", "ng/dL", "23-173", "L"),
    ],
))

# ======================================================================
# Goldfarb / Accureference — 2015-02-18 — ACTH only
# Source PDF: Medical Records/Dr. Goldfarb/Coble_2-18-15.pdf
# ======================================================================
PANELS.append(Panel(
    source=ACCUREF, patient_ref="Patient/ar-A1405221213",
    specimen_id="AR-1502190206",
    panel_name="Hormones (ACTH)",
    collected=dt.datetime(2015, 2, 18, 14, 45, tzinfo=ET),
    source_pdf="Medical Records/Dr. Goldfarb/Coble_2-18-15.pdf",
    labs=[
        Lab("ACTH", "<5.00", "pg/mL", "0-46"),
    ],
))

# ======================================================================
# Goldfarb / Accureference — 2016-01-20 — Major panel
# Source PDF: Medical Records/Dr. Goldfarb/Coble_1-21-16.pdf
# ======================================================================
PANELS.append(Panel(
    source=ACCUREF, patient_ref="Patient/ar-A1405221213",
    specimen_id="AR-1601210146",
    panel_name="CBC + Automated Diff",
    collected=dt.datetime(2016, 1, 20, 16, 30, tzinfo=ET),
    source_pdf="Medical Records/Dr. Goldfarb/Coble_1-21-16.pdf",
    labs=[
        Lab("WBC", "5.3", "10^3/uL", "4.2-11.8"),
        Lab("RBC", "5.28", "10^6/uL", "4.4-5.8"),
        Lab("Hemoglobin", "17.0", "g/dL", "13.1-17.1"),
        Lab("Hematocrit", "51", "%", "40.0-50.4", "H"),
        Lab("MCV", "97", "fL", "80.8-97.4"),
        Lab("MCH", "32.1", "pg", "26.6-33.0"),
        Lab("MCHC", "33.1", "g/dL", "32.0-34.9"),
        Lab("RDW", "13.1", "%", "11.8-15.5"),
        Lab("Platelets", "185", "10^3/uL", "147-365"),
        Lab("MPV", "10.94", "fL", "6.00-12.00"),
        Lab("Segmented %", "57.4", "%", "43.7-73.5"),
        Lab("Segmented #", "3.0", "10^3/uL", "1.80-7.90"),
        Lab("Lymphocytes %", "29.97", "%", "17.9-45.1"),
        Lab("Lymphocytes #", "1.6", "10^3/uL", "1.0-4.0"),
        Lab("Monocytes %", "11.4", "%", "3.8-10.0", "H"),
        Lab("Monocytes #", "0.6", "10^3/uL", "0.2-0.9"),
        Lab("Eosinophils %", "0.84", "%", "0.0-6.1"),
        Lab("Eosinophils #", "0.04", "10^3/uL", "0.0-0.5"),
        Lab("Basophils %", "0.37", "%", "0.0-0.9"),
        Lab("Basophils #", "0.02", "10^3/uL", "0.0-0.1"),
    ],
))
PANELS.append(Panel(
    source=ACCUREF, patient_ref="Patient/ar-A1405221213",
    specimen_id="AR-1601210146",
    panel_name="Comprehensive Metabolic Panel",
    collected=dt.datetime(2016, 1, 20, 16, 30, tzinfo=ET),
    source_pdf="Medical Records/Dr. Goldfarb/Coble_1-21-16.pdf",
    labs=[
        Lab("Sodium", "142", "mmol/L", "136-145"),
        Lab("Potassium", "3.9", "mmol/L", "3.5-5.1"),
        Lab("Chloride", "103", "mmol/L", "98-107"),
        Lab("Alkaline Phosphatase", "46", "U/L", "34-104"),
        Lab("Glucose", "83", "mg/dL", "70-99"),
        Lab("BUN", "11", "mg/dL", "7-25"),
        Lab("Creatinine, Serum", "0.98", "mg/dL", "0.7-1.3"),
        Lab("BUN/Creatinine Ratio", "11", None, "8-28"),
        Lab("Bilirubin, Total", "0.6", "mg/dL", "0.0-1.0"),
        Lab("Calcium", "9.6", "mg/dL", "8.6-10.5"),
        Lab("Protein, Total", "7.8", "g/dL", "6.4-8.9"),
        Lab("Albumin", "4.8", "g/dL", "3.5-5.7"),
        Lab("ALT (SGPT)", "33", "U/L", "7-52"),
        Lab("AST (SGOT)", "24", "U/L", "9-39"),
        Lab("Carbon Dioxide", "29.0", "mEq/L", "17-32"),
        Lab("Globulin", "3.0", "g/L", "1.8-4.0"),
        Lab("A/G Ratio", "1.6", None, "0.8-2.7"),
        Lab("eGFR", "94", "mL/min/1.73", ">60"),
    ],
))
PANELS.append(Panel(
    source=ACCUREF, patient_ref="Patient/ar-A1405221213",
    specimen_id="AR-1601210146",
    panel_name="Additional Tests (Accureference 2016-01-20)",
    collected=dt.datetime(2016, 1, 20, 16, 30, tzinfo=ET),
    source_pdf="Medical Records/Dr. Goldfarb/Coble_1-21-16.pdf",
    labs=[
        Lab("Iron", "79", "ug/dL", "50-212"),
        Lab("TIBC", "361", "ug/dL", "205-512"),
        Lab("UIBC", "282", "ug/dL", "155-355"),
        Lab("Saturation %", "22", "%", "20-55"),
        Lab("Ferritin", "48.6", "ng/mL", "23.9-336.2"),
        Lab("IGF-I", "386", "ng/mL", "82-243", "H"),
        Lab("Free T4", "1.09", "ng/dL", "0.61-1.12"),
        Lab("Free T3", "4.3", "pg/mL", "2.3-4.2", "H"),
        Lab("TSH 3rd Generation", "0.107", "uIU/mL", "0.340-5.600", "L"),
        Lab("Reverse T3, Serum", "19.7", "ng/dL", "9.2-24.1"),
        Lab("Cortisol", "9.1", "ug/dL", "5.0-25.0"),
        Lab("Cortisol, Free by ED/LC-MS/MS", "0.42", "ug/dL", "0.21-1.04"),
        Lab("DHEA-Sulfate", "13.7", "ug/dL", "34.5-568.9", "L"),
        Lab("Estradiol", "40.8", "pg/mL", "0.0-39.8", "H"),
        Lab("Pregnenolone by MS/MS", "7", "ng/dL", "23-173", "L"),
        Lab("Testosterone, Total", "467.66", "ng/dL", "198-679"),
        Lab("Testosterone, Free (Calculated)", "84.00", "%", "24.3-110.2"),
        Lab("Sex Hormone Binding Globulin", "19.2", "nmol/L", "13.2-89.6"),
        Lab("5-a-Dihydrotestosterone, LC-MS/MS", "400.0", "pg/mL", "106.0-719.0"),
        Lab("Vitamin D, 25-Hydroxy", "28.6", "ng/mL", "30-100", "L"),
    ],
))

# ======================================================================
# Goldfarb / Accureference — 2017-09-25 specimen A1405221213 (ACTH-only report)
# Source PDF: Medical Records/Dr. Goldfarb/Coble_09-25-17.pdf
# ======================================================================
PANELS.append(Panel(
    source=ACCUREF, patient_ref="Patient/ar-A1405221213",
    specimen_id="AR-1709270172",
    panel_name="Hormones (ACTH)",
    collected=dt.datetime(2017, 9, 25, 17, 10, tzinfo=ET),
    source_pdf="Medical Records/Dr. Goldfarb/Coble_09-25-17.pdf",
    labs=[
        Lab("ACTH", "<5.00", "pg/mL", "0-46"),
    ],
))

# ======================================================================
# Goldfarb / Accureference — 2017-09-25 — Major panel
# Source PDF: Medical Records/Dr. Goldfarb/Coble_09-27-17.pdf
# ======================================================================
PANELS.append(Panel(
    source=ACCUREF, patient_ref="Patient/ar-A1405221213",
    specimen_id="AR-1709270171",
    panel_name="CBC + Automated Diff",
    collected=dt.datetime(2017, 9, 25, 17, 10, tzinfo=ET),
    source_pdf="Medical Records/Dr. Goldfarb/Coble_09-27-17.pdf",
    labs=[
        Lab("WBC", "9.7", "10^3/uL", "4.2-11.8"),
        Lab("RBC", "5.14", "10^6/uL", "4.4-5.8"),
        Lab("Hemoglobin", "16.5", "g/dL", "13.1-17.1"),
        Lab("Hematocrit", "47", "%", "40-50.4"),
        Lab("MCV", "92", "fL", "80.8-97.4"),
        Lab("MCH", "32.1", "pg", "26.6-33.0"),
        Lab("MCHC", "34.9", "g/dL", "32-34.9"),
        Lab("RDW", "12.9", "%", "11.8-15.5"),
        Lab("Platelets", "184", "10^3/uL", "147-365"),
        Lab("MPV", "11.87", "fL", "6.00-12.00"),
        Lab("Segmented %", "79.5", "%", "43.7-73.5", "H"),
        Lab("Segmented #", "7.7", "10^3/uL", "1.9-7.5", "H"),
        Lab("Lymphocytes %", "13.05", "%", "17.9-45.1", "L"),
        Lab("Lymphocytes #", "1.3", "10^3/uL", "1-4"),
        Lab("Monocytes %", "6.7", "%", "3.8-10"),
        Lab("Monocytes #", "0.6", "10^3/uL", "0.2-0.9"),
        Lab("Eosinophils %", "0.38", "%", "0.0-6.1"),
        Lab("Eosinophils #", "0.04", "10^3/uL", "0.0-0.5"),
        Lab("Basophils %", "0.30", "%", "0.0-0.9"),
        Lab("Basophils #", "0.03", "10^3/uL", "0.0-0.1"),
    ],
))
PANELS.append(Panel(
    source=ACCUREF, patient_ref="Patient/ar-A1405221213",
    specimen_id="AR-1709270171",
    panel_name="Comprehensive Metabolic Panel",
    collected=dt.datetime(2017, 9, 25, 17, 10, tzinfo=ET),
    source_pdf="Medical Records/Dr. Goldfarb/Coble_09-27-17.pdf",
    labs=[
        Lab("Sodium", "143", "mmol/L", "136-145"),
        Lab("Potassium", "4.3", "mmol/L", "3.5-5.1"),
        Lab("Chloride", "102", "mmol/L", "98-107"),
        Lab("Carbon Dioxide", "25.0", "mEq/L", "17-32"),
        Lab("Glucose", "79", "mg/dL", "70-99"),
        Lab("BUN", "12", "mg/dL", "7-25"),
        Lab("Creatinine, Serum", "0.90", "mg/dL", "0.7-1.3"),
        Lab("BUN/Creatinine Ratio", "13", None, "8-28"),
        Lab("Bilirubin, Total", "0.7", "mg/dL", "0.2-1.0"),
        Lab("Calcium", "9.9", "mg/dL", "8.6-10.5"),
        Lab("Protein, Total", "7.4", "g/dL", "6.6-8.2"),
        Lab("Albumin", "5.0", "g/dL", "3.5-5.7"),
        Lab("Alkaline Phosphatase", "58", "U/L", "34-104"),
        Lab("ALT (SGPT)", "16", "U/L", "7-52"),
        Lab("AST (SGOT)", "21", "U/L", "11-39"),
        Lab("Globulin", "2.4", "g/dL", "1.8-4.0"),
        Lab("A/G Ratio", "2.1", None, "0.8-2.7"),
        Lab("eGFR", "103", "mL/min", ">60"),
    ],
))
PANELS.append(Panel(
    source=ACCUREF, patient_ref="Patient/ar-A1405221213",
    specimen_id="AR-1709270171",
    panel_name="Additional Tests (Accureference 2017-09-25)",
    collected=dt.datetime(2017, 9, 25, 17, 10, tzinfo=ET),
    source_pdf="Medical Records/Dr. Goldfarb/Coble_09-27-17.pdf",
    labs=[
        Lab("Iron", "50", "ug/dL", "50-175"),
        Lab("TIBC", "357", "ug/dL", "205-512"),
        Lab("UIBC", "307", "ug/dL", "155-300", "H"),
        Lab("Saturation %", "14", "%", "20-55", "L"),
        Lab("Ferritin", "142.9", "ng/mL", "23.9-336.2"),
        Lab("CRP Cardio/Neo (HS)", "1.0", "mg/L", "0.0-3.0"),
        Lab("IGF-I", "320", "ng/mL", "82-242", "H"),
        Lab("Free T4", "0.86", "ng/dL", "0.61-1.12"),
        Lab("Free T3", "2.9", "pg/mL", "2.3-4.2"),
        Lab("TSH 3rd Generation", "1.395", "uIU/mL", "0.340-4.410"),
        Lab("T3 Reverse", "18.0", "ng/dL", "9.0-27.0"),
        Lab("Aldosterone", "<3.00", "ng/dL", "<3.0-39.2"),
        Lab("DHEA-Sulfate", "30.4", "ug/dL", "34.5-568.9", "L"),
        Lab("Estradiol", "49.6", "pg/mL", "0.0-39.8", "H"),
        Lab("Testosterone, Total", "454.29", "ng/dL", "198-679"),
        Lab("Testosterone, Free (Calculated)", "60.00", "%", "24.3-110.2"),
        Lab("Sex Hormone Binding Globulin", "26.5", "nmol/L", "13.2-89.6"),
        Lab("Vitamin B12", "1152", "pg/mL", "180-914", "H"),
        Lab("Vitamin D, 25-Hydroxy", "26.3", "ng/mL", "30-100", "L"),
        Lab("Pregnenolone", "23.9", "ng/dL", "13-208"),
        Lab("Folate RBC", "547", "ng/mL RBC", ">280"),
        Lab("Dihydrotestosterone", "42", "ng/dL", "30-85"),
    ],
))

# ======================================================================
# Goldfarb / Accureference — 2024-02-22 — Major panel
# Source PDF: Downloads/Coble labs.pdf
# ======================================================================
PANELS.append(Panel(
    source=ACCUREF, patient_ref="Patient/ar-A1405221213",
    specimen_id="AR-2405401534",
    panel_name="CBC with Automated Diff",
    collected=dt.datetime(2024, 2, 22, 11, 54, tzinfo=ET),
    source_pdf="Downloads/Coble labs.pdf",
    labs=[
        Lab("WBC", "9.3", "10^3/uL", "4.2-11.8"),
        Lab("RBC", "5.3", "10^6/uL", "4.4-5.8"),
        Lab("Hemoglobin", "16.9", "g/dL", "13.1-17.1"),
        Lab("Hematocrit", "50.6", "%", "40.0-50.4", "H"),
        Lab("MCV", "96.1", "fL", "80.8-97.4"),
        Lab("MCH", "32.1", "pg", "26.6-33.0"),
        Lab("MCHC", "33.4", "g/dL", "32.0-34.9"),
        Lab("RDW", "13.9", "%", "11.8-15.5"),
        Lab("Platelets", "219", "10^3/uL", "147-365"),
        Lab("MPV", "10.4", "fL", "6.0-12.0"),
        Lab("Segmented %", "82.2", "%", "43.7-73.5", "H"),
        Lab("Segmented #", "7.6", "10^3/uL", "1.9-7.5", "H"),
        Lab("Lymphocytes %", "7.8", "%", "17.9-45.1", "L"),
        Lab("Lymphocytes #", "0.7", "10^3/uL", "1.0-4.0", "L"),
        Lab("Monocytes %", "8.6", "%", "3.8-10"),
        Lab("Monocytes #", "0.8", "10^3/uL", "0.2-0.9"),
        Lab("Eosinophils %", "0.6", "%", "0.0-6.1"),
        Lab("Eosinophils #", "0.1", "10^3/uL", "0.0-0.5"),
        Lab("Basophils %", "0.8", "%", "0.0-1.3"),
        Lab("Basophils #", "0.1", "10^3/uL", "0.0-0.1"),
    ],
))
PANELS.append(Panel(
    source=ACCUREF, patient_ref="Patient/ar-A1405221213",
    specimen_id="AR-2405401534",
    panel_name="Comprehensive Metabolic Panel",
    collected=dt.datetime(2024, 2, 22, 11, 54, tzinfo=ET),
    source_pdf="Downloads/Coble labs.pdf",
    labs=[
        Lab("Sodium", "145", "mmol/L", "136-145"),
        Lab("Potassium", "3.4", "mmol/L", "3.5-5.1", "L"),
        Lab("Chloride", "105", "mmol/L", "98-107"),
        Lab("Carbon Dioxide", "31", "mEq/L", "19-32"),
        Lab("Glucose", "114", "mg/dL", "65-99", "H"),
        Lab("BUN", "19", "mg/dL", "7-25"),
        Lab("Creatinine", "1.1", "mg/dL", "0.7-1.3"),
        Lab("BUN/Creatinine Ratio", "17", None, "8-28"),
        Lab("Bilirubin, Total", "0.7", "mg/dL", "0.2-1.0"),
        Lab("Calcium", "8.9", "mg/dL", "8.6-10.5"),
        Lab("Protein, Total", "6.8", "g/dL", "6.0-8.3"),
        Lab("Albumin", "4.1", "g/dL", "3.5-5.7"),
        Lab("Alkaline Phosphatase", "50", "U/L", "20-130"),
        Lab("ALT (SGPT)", "37", "U/L", "7-52"),
        Lab("AST (SGOT)", "20", "U/L", "11-39"),
        Lab("Globulin", "2.7", "g/dL", "1.8-4.0"),
        Lab("A/G Ratio", "1.5", None, "0.8-2.7"),
        Lab("eGFR", "87", "mL/min", ">=60"),
    ],
))
PANELS.append(Panel(
    source=ACCUREF, patient_ref="Patient/ar-A1405221213",
    specimen_id="AR-2405401534",
    panel_name="Additional Tests (Accureference 2024-02-22)",
    collected=dt.datetime(2024, 2, 22, 11, 54, tzinfo=ET),
    source_pdf="Downloads/Coble labs.pdf",
    labs=[
        Lab("Iron", "99", "ug/dL", "50-175"),
        Lab("TIBC", "397", "ug/dL", "205-512"),
        Lab("UIBC", "298", "ug/dL", "155-300"),
        Lab("Saturation %", "25", "%", "20-55"),
        Lab("Ferritin", "56.3", "ng/mL", "23.9-336.2"),
        Lab("CRP Cardio/Neo (HS)", "0.2", "mg/L", "0.0-3.0"),
        Lab("Hemoglobin A1c", "5.2", "%", "4.0-5.6"),
        Lab("IGF-I", "313", "ng/mL", "67-244", "H"),
        Lab("TSH 3rd Generation", "1.475", "uIU/mL", "0.340-4.410"),
        Lab("Free T3", "3.0", "pg/mL", "2.5-3.9"),
        Lab("Free T4", "0.94", "ng/dL", "0.54-1.24"),
        Lab("T3 Reverse", "12.4", "ng/dL", "9.0-27.0"),
        Lab("Cortisol", "<1.00", "ug/dL", "5.0-25.0", "L"),
        Lab("DHEA-Sulfate", "27.3", "ug/dL", "106.0-464.0", "L"),
        Lab("FSH", "7.47", "mIU/mL", "1.27-19.26"),
        Lab("LH", "2.46", "mIU/mL", "1.24-8.62"),
        Lab("Testosterone, Total", "343", "ng/dL", "198-679"),
        Lab("Testosterone, Free (Calculated)", "52.66", "%", "24.3-110.2"),
        Lab("Sex Hormone Binding Globulin", "22.6", "nmol/L", "13.2-89.6"),
        Lab("Dihydrotestosterone-5a LC/MS", "336.8", "pg/mL", "106.0-719.0"),
        Lab("Pregnenolone", "17.9", "ng/dL", "13.0-208.0"),
        Lab("Estradiol", "19.0", "pg/mL", "<31.5"),
        Lab("Vitamin D, 25-Hydroxy", "38.7", "ng/mL", "30.0-100.0"),
        Lab("Magnesium, RBC", "6.4", "mg/dL", "4.0-6.4"),
    ],
))

# ======================================================================
# Millennium Health PGT — 2015-02-18 — Pharmacogenetic testing
# Source PDF: Downloads/Coble_pgt.pdf
# User wanted this "under documents or something" — ingested as labs so it
# appears alongside other records. Each gene is one "lab" with genotype as
# textValue and predicted phenotype as reference text for context.
# ======================================================================
PANELS.append(Panel(
    source=MILLENNIUM, patient_ref="Patient/millennium-AA7347749",
    specimen_id="MILL-GS970339",
    panel_name="Pharmacogenetic Test (MAPP)",
    collected=dt.datetime(2015, 2, 18, 0, 0, tzinfo=ET),
    source_pdf="Downloads/Coble_pgt.pdf",
    labs=[
        Lab("CYP2B6", "*1/*6", None, "Intermediate Metabolizer"),
        Lab("CYP2C19", "*1/*2", None, "Intermediate Metabolizer"),
        Lab("MTHFR", "C/T (C677T); A/C (A1298C)", None, "Greatly Reduced Activity"),
        Lab("UGT2B15", "*1/*2", None, "Intermediate Metabolizer"),
        Lab("COMT", "A/G", None, "Normal Activity"),
        Lab("CYP2C9", "*1/*1", None, "Extensive (Normal) Metabolizer"),
        Lab("CYP2D6", "*2/*35", None, "Extensive (Normal) Metabolizer"),
        Lab("CYP3A4/CYP3A5", "*1/*1; *3/*3", None, "Intermediate Metabolizer"),
        Lab("OPRM1", "A/A", None, "Normal Expressor"),
    ],
))

# ======================================================================
# IGeneX / AcuDart — 2025-02-19 — Tick-Borne Disease Screen
# Source PDF: Downloads/SP-DYKWJB9BT Lab Report (2).pdf
# ======================================================================
PANELS.append(Panel(
    source=IGENEX, patient_ref="Patient/igenex-SP-DYKWJB9BT",
    specimen_id="IGENEX-SP-DYKWJB9BT",
    panel_name="Tick-Borne Disease Screen Panel",
    collected=dt.datetime(2025, 2, 19, 0, 0, tzinfo=ET),
    source_pdf="Downloads/SP-DYKWJB9BT Lab Report (2).pdf",
    labs=[
        Lab("Lyme Broad Coverage Ab Assay", "Negative", None, "Negative"),
        Lab("TBRF Broad Coverage Ab Assay", "Positive", None, "Negative", "A"),
        Lab("Babesia Broad Coverage Ab Assay", "Negative", None, "Negative"),
        Lab("Bartonella Broad Coverage Ab Assay", "Negative", None, "Negative"),
    ],
))


# ======================================================================
# Mayo Clinic — 2020-08-19 (most panels) + 2020-09-01 (catecholamine) + 2020-09-02 (autonomic reflex)
# MRN 12-768-979
# ======================================================================
MAYO_COLL_AM = dt.datetime(2020, 8, 19, 9, 42, tzinfo=ET)  # most blood draws
MAYO_COLL_URINE = dt.datetime(2020, 8, 19, 10, 20, tzinfo=ET)
MAYO_COLL_24HR = dt.datetime(2020, 8, 20, 10, 0, tzinfo=ET)
MAYO_COLL_CATECH = dt.datetime(2020, 9, 1, 11, 50, tzinfo=ET)
MAYO_COLL_AUTO_REFLEX = dt.datetime(2020, 9, 2, 0, 0, tzinfo=ET)

PANELS.append(Panel(
    source=MAYO, patient_ref="Patient/mayo-12-768-979",
    specimen_id="MAYO-2020-08-19-CBC",
    panel_name="Complete Blood Count",
    collected=MAYO_COLL_AM,
    source_pdf="Labs/Mayo - All Around 1st September/Complete Blood Count.pdf",
    labs=[
        Lab("Leukocytes (WBC)", "6.0", "x10(9)/L", "3.4-9.6"),
        Lab("Erythrocytes (RBC)", "4.86", "x10(12)/L", "4.35-5.65"),
        Lab("Hemoglobin", "15.6", "g/dL", "13.2-16.6"),
        Lab("Hematocrit", "45.3", "%", "38.3-48.6"),
        Lab("MCV", "93.2", "fL", "78.2-97.9"),
        Lab("MCH", "32.1", "pg", "25.4-32.7"),
        Lab("MCHC", "34.4", "g/dL", "32.1-35.6"),
        Lab("RDW CV", "11.7", "%", "11.8-14.5", "L"),
        Lab("RDW SD", "40.4", "fL", "35.1-43.9"),
        Lab("Platelet Count", "204", "x10(9)/L", "135-317"),
        Lab("Mean Platelet Volume", "10.6", "fL", "7.6-10.8"),
        Lab("Neutrophils %", "64.2", "%", "50.0-75.0"),
        Lab("Immature Granulocytes %", "0.3", "%", "0.0-3.0"),
        Lab("Lymphocytes %", "23.9", "%", "18.0-42.0"),
        Lab("Monocytes %", "8.1", "%", "2.0-11.0"),
        Lab("Eosinophils %", "3.2", "%", "1.0-3.0", "H"),
        Lab("Basophils %", "0.3", "%", "0.0-2.0"),
        Lab("Neutrophils", "3.82", "x10(9)/L", "1.56-6.45"),
        Lab("Lymphocytes", "1.42", "x10(9)/L", "0.95-3.07"),
        Lab("Monocytes", "0.48", "x10(9)/L", "0.26-0.81"),
        Lab("Eosinophils", "0.19", "x10(9)/L", "0.03-0.48"),
        Lab("Basophils", "0.0", "x10(9)/L", "0.0-0.1"),
    ],
))
PANELS.append(Panel(
    source=MAYO, patient_ref="Patient/mayo-12-768-979",
    specimen_id="MAYO-2020-08-19-CMP",
    panel_name="Extended Metabolic/Electrolyte Panel",
    collected=MAYO_COLL_AM,
    source_pdf="Labs/Mayo - All Around 1st September/Extended Metabolic-Electrolyte panel.pdf",
    labs=[
        Lab("Potassium", "3.9", "mmol/L", "3.6-5.2"),
        Lab("Sodium", "141", "mmol/L", "135-145"),
        Lab("Chloride", "102", "mmol/L", "98-107"),
        Lab("Bicarbonate", "26", "mmol/L", "22-29"),
        Lab("Anion Gap", "13", None, "7-15"),
        Lab("BUN", "15", "mg/dL", "8-24"),
        Lab("Creatinine", "1.01", "mg/dL", "0.74-1.35"),
        Lab("eGFR Black", ">90", "mL/min/BSA", ">=60"),
        Lab("eGFR Non-Black", ">90", "mL/min/BSA", ">=60"),
        Lab("Calcium, Total", "9.3", "mg/dL", "8.6-10.0"),
        Lab("Glucose", "86", "mg/dL", "70-140"),
        Lab("Protein, Total", "7.1", "g/dL", "6.3-7.9"),
        Lab("Albumin", "4.5", "g/dL", "3.5-5.0"),
        Lab("AST", "29", "U/L", "8-48"),
        Lab("Alkaline Phosphatase", "46", "U/L", "40-129"),
        Lab("ALT", "30", "U/L", "7-55"),
        Lab("Bilirubin, Total", "0.5", "mg/dL", "<=1.2"),
    ],
))
PANELS.append(Panel(
    source=MAYO, patient_ref="Patient/mayo-12-768-979",
    specimen_id="MAYO-2020-08-19-LIPID",
    panel_name="Fasting Lipid (Cholesterol) Screening",
    collected=MAYO_COLL_AM,
    source_pdf="Labs/Mayo - All Around 1st September/Fasting Lipid (Cholesterol) Screening.pdf",
    labs=[
        Lab("Cholesterol, Total", "167", "mg/dL", "<200"),
        Lab("Triglycerides", "89", "mg/dL", "<150"),
        Lab("Cholesterol, HDL", "56", "mg/dL", ">=40"),
        Lab("Calculated LDL", "93", "mg/dL", "<100"),
        Lab("Non HDL Cholesterol", "111", "mg/dL", "<130"),
    ],
))
PANELS.append(Panel(
    source=MAYO, patient_ref="Patient/mayo-12-768-979",
    specimen_id="MAYO-2020-08-19-TSH",
    panel_name="S-TSH (Thyroid Function)",
    collected=MAYO_COLL_AM,
    source_pdf="Labs/Mayo - All Around 1st September/S-TSH.pdf",
    labs=[
        Lab("TSH, Sensitive", "2.0", "mIU/L", "0.3-4.2"),
    ],
))
PANELS.append(Panel(
    source=MAYO, patient_ref="Patient/mayo-12-768-979",
    specimen_id="MAYO-2020-08-19-MG",
    panel_name="Magnesium Level",
    collected=MAYO_COLL_AM,
    source_pdf="Labs/Mayo - All Around 1st September/Magnesium Level.pdf",
    labs=[
        Lab("Magnesium", "2.1", "mg/dL", "1.7-2.3"),
    ],
))
PANELS.append(Panel(
    source=MAYO, patient_ref="Patient/mayo-12-768-979",
    specimen_id="MAYO-2020-08-19-CORTISOL",
    panel_name="Cortisol",
    collected=MAYO_COLL_AM,
    source_pdf="Labs/Mayo - All Around 1st September/CORTISOL - With Taking Meds.pdf",
    labs=[
        Lab("Cortisol, AM Result", "7.7", "mcg/dL", "7-25"),
    ],
))
PANELS.append(Panel(
    source=MAYO, patient_ref="Patient/mayo-12-768-979",
    specimen_id="MAYO-2020-08-19-TESTO",
    panel_name="Testosterone Total Free",
    collected=MAYO_COLL_AM,
    source_pdf="Labs/Mayo - All Around 1st September/TESTOSTERONE TOTAL FREE.pdf",
    labs=[
        Lab("Testosterone, Free", "14.1", "ng/dL", "4.65-18.1"),
        Lab("Testosterone, Total by Mass Spectrometry", "641", "ng/dL", "240-950"),
    ],
))
PANELS.append(Panel(
    source=MAYO, patient_ref="Patient/mayo-12-768-979",
    specimen_id="MAYO-2020-08-19-URINALYSIS",
    panel_name="Microscopic Urinalysis",
    collected=MAYO_COLL_URINE,
    source_pdf="Labs/Mayo - All Around 1st September/Microscopic urinalysis.pdf",
    labs=[
        Lab("Source", "Clean Catch", None, None),
        Lab("Color", "Colorless", None, None),
        Lab("Clarity", "Clear", None, None),
        Lab("Glucose, Urine", "Negative", "mg/dL", "Negative"),
        Lab("Ketones", "Negative", None, "Negative"),
        Lab("Hemoglobin, QL", "Negative", None, "Negative"),
        Lab("Protein, Urine", "Negative", "mg/dL", "Negative"),
        Lab("Nitrite, Urine", "Negative", None, "Negative"),
        Lab("Bilirubin", "Negative", None, "Negative"),
        Lab("Specific Gravity", "1.010", None, "1.002-1.030"),
        Lab("pH, Urine", "7.5", None, "5.0-8.0"),
        Lab("Urobilinogen", "Normal", None, "Normal"),
        Lab("Leukocyte Esterase", "Negative", None, "Negative"),
        Lab("White Blood Cells (Urine)", "<1", "/hpf", "0-3"),
        Lab("Urine RBC", "<1", "/hpf", "0-2"),
    ],
))
PANELS.append(Panel(
    source=MAYO, patient_ref="Patient/mayo-12-768-979",
    specimen_id="MAYO-2020-08-20-UNA24",
    panel_name="Sodium, 24 HR Urine",
    collected=MAYO_COLL_24HR,
    source_pdf="Labs/Mayo - All Around 1st September/SODIUM, 24 HR URINE.pdf",
    labs=[
        Lab("Sodium, Urine 24 Hour", "341", "mmol/24h", "41-227", "H"),
        Lab("Collection Duration", "24", "h", None),
        Lab("Urine Volume", "3875", "mL", None),
        Lab("Sodium Concentration", "88", "mmol/L", None),
    ],
))
PANELS.append(Panel(
    source=MAYO, patient_ref="Patient/mayo-12-768-979",
    specimen_id="MAYO-2020-08-19-DYSAUTO",
    panel_name="Autoimmune Dysautonomia Evaluation",
    collected=MAYO_COLL_AM,
    source_pdf="Labs/Mayo - All Around 1st September/AUTOIMMUNE DYSAUTONOMIA EVAL.pdf",
    labs=[
        Lab("Dysautonomia, Interpretation", "No informative autoantibodies detected", None, None),
        Lab("ACh Receptor (Muscle) Binding Ab", "0.00", "nmol/L", "<=0.02"),
        Lab("AChR Ganglionic Neuronal Ab", "0.00", "nmol/L", "<=0.02"),
        Lab("ANNA-1", "Negative", "titer", "<1:240"),
        Lab("DPPX Ab IFA", "Negative", None, "Negative"),
        Lab("GAD65 Ab Assay", "0.00", "nmol/L", "<=0.02"),
        Lab("Neuronal (V-G) K+ Channel Ab", "0.00", "nmol/L", "<=0.02"),
        Lab("N-Type Calcium Channel Ab", "0.00", "nmol/L", "<=0.03"),
        Lab("P/Q-Type Calcium Channel Ab", "0.00", "nmol/L", "<=0.02"),
        Lab("Striational (Striated Muscle) Ab", "Negative", "titer", "<1:120"),
    ],
))
PANELS.append(Panel(
    source=MAYO, patient_ref="Patient/mayo-12-768-979",
    specimen_id="MAYO-2020-09-01-CATECH",
    panel_name="Catecholamine, Endocrine Study",
    collected=MAYO_COLL_CATECH,
    source_pdf="Labs/Mayo - All Around 1st September/CATECHOLAMINE, ENDOCRINE STUDY.pdf",
    labs=[
        Lab("Norepinephrine, Supine", "55", "pg/mL", "70-750", "L"),
        Lab("Epinephrine, Supine", "<25", "pg/mL", "<111"),
        Lab("Dopamine, Supine", "<25", "pg/mL", "<30"),
        Lab("Norepinephrine, Standing", "249", "pg/mL", "200-1700"),
        Lab("Epinephrine, Standing", "<25", "pg/mL", "<141"),
        Lab("Dopamine, Standing", "<25", "pg/mL", "<30"),
    ],
))
PANELS.append(Panel(
    source=MAYO, patient_ref="Patient/mayo-12-768-979",
    specimen_id="MAYO-2020-09-02-AUTOREFLEX",
    panel_name="Autonomic Reflex Screen",
    collected=MAYO_COLL_AUTO_REFLEX,
    source_pdf="Labs/Mayo - All Around 1st September/AUTONOMIC REFLEX SCREEN.pdf",
    labs=[
        Lab("QSART - Forearm Sweat Output", "2.68", "uL", ">=0.43"),
        Lab("QSART - Proximal Leg Sweat Output", "1.54", "uL", ">=0.73"),
        Lab("QSART - Distal Leg Sweat Output", "0.02", "uL", ">=0.82", "L"),
        Lab("QSART - Foot Sweat Output", "0.82", "uL", ">=0.33"),
        Lab("Deep Breathing Heart Rate Range", "20.9", "bpm", ">12"),
        Lab("Valsalva Ratio", "1.75", None, ">1.52"),
        Lab("Valsalva Vagal BRS", "8.61", "msec/mmHg", ">1.43"),
        Lab("Valsalva BP Recovery Time", "1.3", "sec", "<2.68"),
        Lab("CASS (Sudomotor)", "1", None, "0-3"),
        Lab("CASS (Vagal)", "0", None, "0-3"),
        Lab("CASS (Adrenergic)", "0", None, "0-4"),
        Lab("CASS (Total)", "1", None, "0-10"),
    ],
))


# ---- Parsing helpers ----
def parse_value(result: str) -> tuple[float | None, str | None]:
    s = (result or "").strip()
    try:
        return float(s), None
    except ValueError:
        return None, s


def parse_ref(ref: str | None) -> tuple[float | None, float | None, str | None]:
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
    # Preserve % and # semantics before stripping — otherwise "Segmented %" and
    # "Segmented #" collapse to the same slug and collide in a CBC panel.
    s = name.lower().replace("%", "-pct").replace("#", "-num")
    s = re.sub(r"[^a-z0-9]+", "-", s)
    return s.strip("-")


# ---- SQL ----
LAB_INSERT = """
INSERT OR IGNORE INTO lab_observations
  (sourceSystem, sourceName, fhirReference, resourceId,
   patientFhirId, diagnosticReportReference, loincCode,
   testName, numericValue, textValue, unit,
   referenceLow, referenceHigh, referenceText,
   interpretation, effectiveAt, status, importedAt,
   canonicalTestName, canonicalPanelName,
   serviceRequestReference, serviceRequestDisplay)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
"""

SRC_INSERT = """
INSERT OR IGNORE INTO source_records
  (syncJobId, sourceSystem, sourceName, resourceType,
   resourceId, fhirReference, rawJson, importedAt)
VALUES (NULL, ?, ?, ?, ?, ?, ?, ?)
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
    lab_rows: list[tuple] = []
    src_rows: list[tuple] = []

    for panel in PANELS:
        ss, sn = panel.source
        dr_ref = f"DiagnosticReport/{panel.specimen_id}"
        effective_ms = int(panel.collected.timestamp() * 1000)

        # DiagnosticReport source_record (one per panel)
        dr_raw = {
            "resourceType": "DiagnosticReport",
            "id": panel.specimen_id,
            "panel": panel.panel_name,
            "source": ss,
            "patient": panel.patient_ref,
            "collected": panel.collected.isoformat(),
            "sourcePdf": panel.source_pdf,
            "entries": [
                {"test": l.test, "result": l.result, "units": l.units,
                 "ref": l.ref, "flag": l.flag}
                for l in panel.labs
            ],
        }
        src_rows.append((
            ss, sn, "DiagnosticReport", panel.specimen_id, dr_ref,
            json.dumps(dr_raw, separators=(",", ":")), now_ms,
        ))

        for lab in panel.labs:
            num, text = parse_value(lab.result)
            low, high, ref_text = parse_ref(lab.ref)
            test_slug = slugify(lab.test)
            obs_id = f"{panel.specimen_id}#{test_slug}"

            lab_rows.append((
                ss, sn, f"Observation/{obs_id}", obs_id,
                panel.patient_ref, dr_ref, None,
                lab.test, num, text, lab.units,
                low, high, ref_text,
                (lab.flag.upper() if lab.flag else None),
                effective_ms, "final", now_ms,
                test_slug or None, None,
                dr_ref, panel.panel_name,
            ))

            obs_raw = {
                "resourceType": "Observation", "id": obs_id,
                "panel": panel.panel_name,
                "test": lab.test, "result": lab.result, "units": lab.units,
                "referenceRange": lab.ref, "flag": lab.flag,
                "collected": panel.collected.isoformat(),
                "sourcePdf": panel.source_pdf,
                "specimenId": panel.specimen_id,
            }
            src_rows.append((
                ss, sn, "Observation", obs_id, f"Observation/{obs_id}",
                json.dumps(obs_raw, separators=(",", ":")), now_ms,
            ))

    print(f"Parsed {len(lab_rows)} lab rows across {len(PANELS)} panels.")
    print(f"source_records: {len(src_rows)} ({len(PANELS)} DRs + {len(lab_rows)} Observations)\n")
    breakdown: dict[str, int] = {}
    for r in lab_rows:
        breakdown[r[0]] = breakdown.get(r[0], 0) + 1
    for s, c in sorted(breakdown.items(), key=lambda kv: -kv[1]):
        print(f"  {c:4d}  {s}")

    if not args.execute:
        print("\nDRY RUN — no changes written. Re-run with --execute to apply.")
        return 0

    con = sqlite3.connect(db_path)
    try:
        con.execute("BEGIN")
        lab_n = 0
        for r in lab_rows:
            cur = con.execute(LAB_INSERT, r)
            if cur.rowcount > 0:
                lab_n += 1
        src_n = 0
        for r in src_rows:
            cur = con.execute(SRC_INSERT, r)
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
