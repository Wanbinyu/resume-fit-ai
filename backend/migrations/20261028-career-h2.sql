-- Existing H2 file databases only. Run after schema.sql; idempotent across restarts.
-- H2 TEXT already supports large values, so MySQL-specific MODIFY statements are unnecessary.
ALTER TABLE analysis_tasks ADD COLUMN IF NOT EXISTS input_snapshot_json CLOB;
