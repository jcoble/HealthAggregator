ALTER TABLE lab_observations ADD COLUMN canonicalPanelName TEXT;
ALTER TABLE lab_observations ADD COLUMN canonicalTestName TEXT;
CREATE INDEX IF NOT EXISTS index_lab_observations_canonicalPanelName ON lab_observations (canonicalPanelName);
CREATE INDEX IF NOT EXISTS index_lab_observations_canonicalTestName ON lab_observations (canonicalTestName);
