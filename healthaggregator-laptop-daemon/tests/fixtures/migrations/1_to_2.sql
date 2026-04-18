ALTER TABLE lab_observations ADD COLUMN serviceRequestReference TEXT;
ALTER TABLE lab_observations ADD COLUMN serviceRequestDisplay TEXT;
CREATE INDEX IF NOT EXISTS index_lab_observations_serviceRequestReference ON lab_observations (serviceRequestReference);
