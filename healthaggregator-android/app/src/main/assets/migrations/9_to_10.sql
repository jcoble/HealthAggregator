-- 9 → 10: re-purge Condition source_records.
-- Migration 7→8 deleted them locally, but the subsequent pull from the laptop daemon
-- re-inserted them via insertAllIgnore because the sync merge had no Condition filter.
-- SyncRepository now filters resourceType='Condition' out of both push and pull, so
-- once this migration runs and the next sync completes, Condition blobs cannot re-enter
-- either DB through any code path.
DELETE FROM source_records WHERE resourceType = 'Condition';
