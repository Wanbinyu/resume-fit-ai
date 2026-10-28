-- Existing MySQL databases: run ONCE after backing up; fresh databases use schema.sql.
ALTER TABLE analysis_tasks ADD COLUMN input_snapshot_json MEDIUMTEXT NULL;
ALTER TABLE analysis_tasks MODIFY COLUMN result_json MEDIUMTEXT NULL;
ALTER TABLE analysis_reports MODIFY COLUMN report_json MEDIUMTEXT NOT NULL;
