CREATE TABLE IF NOT EXISTS vitals_observations_new (
	`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
	`sourceSystem` TEXT NOT NULL,
	`sourceName` TEXT NOT NULL,
	`fhirReference` TEXT NOT NULL,
	`resourceId` TEXT NOT NULL,
	`patientFhirId` TEXT,
	`loincCode` TEXT,
	`code` TEXT NOT NULL,
	`displayName` TEXT NOT NULL,
	`numericValue` REAL,
	`unit` TEXT,
	`componentCode` TEXT NOT NULL,
	`effectiveAt` INTEGER,
	`importedAt` INTEGER NOT NULL
);
INSERT INTO vitals_observations_new (
	id, sourceSystem, sourceName, fhirReference, resourceId, patientFhirId,
	loincCode, code, displayName, numericValue, unit, componentCode,
	effectiveAt, importedAt
)
SELECT
	id, sourceSystem, sourceName, fhirReference, resourceId, patientFhirId,
	loincCode, code, displayName, numericValue, unit, COALESCE(componentCode, ''),
	effectiveAt, importedAt
FROM vitals_observations
WHERE id IN (
	SELECT MAX(id) FROM vitals_observations
	GROUP BY sourceSystem, fhirReference, COALESCE(componentCode, '')
);
DROP TABLE vitals_observations;
ALTER TABLE vitals_observations_new RENAME TO vitals_observations;
CREATE UNIQUE INDEX IF NOT EXISTS index_vitals_observations_sourceSystem_fhirReference_componentCode ON vitals_observations (sourceSystem, fhirReference, componentCode);
CREATE INDEX IF NOT EXISTS index_vitals_observations_loincCode_effectiveAt ON vitals_observations (loincCode, effectiveAt);
