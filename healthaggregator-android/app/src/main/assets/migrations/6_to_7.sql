-- 6 → 7: remove conditions data.
-- User explicitly does not want any clinical conditions to influence diagnosis-style
-- reasoning. The typed conditions table is wiped, and FhirImportService no longer
-- routes Condition resources into it. The table itself stays (entity still registered)
-- but nothing feeds it and nothing reads from it in the UI, LLM context, or sync.
-- Shipped over /sync/migrate so the laptop daemon DB gets the same wipe on next sync.
DELETE FROM conditions;
