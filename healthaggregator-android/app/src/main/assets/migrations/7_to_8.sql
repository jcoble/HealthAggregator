-- 7 → 8: purge Condition raw blobs from source_records.
-- Follow-up to migration 6→7 (which wiped the typed conditions table). User doesn't
-- want Condition FHIR to influence anything — including raw-text searches over
-- source_records. FhirImportService now also drops Condition resources at ingest,
-- so this migration is a one-time cleanup of blobs already stored. Ships over
-- /sync/migrate so the laptop daemon DB gets the same purge on next sync.
DELETE FROM source_records WHERE resourceType = 'Condition';
