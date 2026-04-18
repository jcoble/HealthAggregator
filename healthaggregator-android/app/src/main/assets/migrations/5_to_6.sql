-- Fix LabCorp labs that were ingested before serviceRequestReference support.
-- Both the CommonHealth FHIR import and the hand-transcribed PDF import set
-- diagnosticReportReference but left serviceRequestReference null, so LabCorp
-- labs were filtered out of the home "Labs" panel list (LabDao.observePanels
-- requires serviceRequestReference IS NOT NULL).
--
-- We also clear canonicalPanelName on the PDF rows because those were stored as
-- kebab-case slugs that look wrong in the UI. After this migration runs, the
-- next app startup triggers RecordsRepository.ensureNamesNormalized, which
-- re-derives canonicalPanelName from serviceRequestDisplay via
-- LabNameNormalizer (so it matches the Cleveland/Summa convention).

-- CommonHealth FHIR LabCorp rows: canonicalPanelName was never set.
-- Pull the panel's human display name from the DiagnosticReport source_record
-- that was stored alongside the Observations.
UPDATE lab_observations
SET serviceRequestReference = diagnosticReportReference,
    serviceRequestDisplay = COALESCE(
        (SELECT json_extract(sr.rawJson, '$.code.text')
           FROM source_records sr
          WHERE sr.sourceSystem = 'labcorp'
            AND sr.resourceType = 'DiagnosticReport'
            AND sr.fhirReference = lab_observations.diagnosticReportReference
          LIMIT 1),
        lab_observations.testName)
WHERE sourceSystem = 'labcorp'
  AND serviceRequestReference IS NULL
  AND diagnosticReportReference IS NOT NULL
  AND canonicalPanelName IS NULL;

-- PDF LabCorp rows: canonicalPanelName was set to a slug.
-- Set a panel key that differentiates sub-panels within a single specimen, copy
-- the pretty panel title from the Observation source_record, and null out the
-- slug so ensureNamesNormalized backfills a proper Title Case name.
UPDATE lab_observations
SET serviceRequestReference = diagnosticReportReference || '#' || canonicalPanelName,
    serviceRequestDisplay = COALESCE(
        (SELECT json_extract(sr.rawJson, '$.panel')
           FROM source_records sr
          WHERE sr.sourceSystem = 'labcorp'
            AND sr.resourceType = 'Observation'
            AND sr.fhirReference = lab_observations.fhirReference
          LIMIT 1),
        REPLACE(lab_observations.canonicalPanelName, '-', ' ')),
    canonicalPanelName = NULL
WHERE sourceSystem = 'labcorp'
  AND serviceRequestReference IS NULL
  AND diagnosticReportReference IS NOT NULL
  AND canonicalPanelName IS NOT NULL;
