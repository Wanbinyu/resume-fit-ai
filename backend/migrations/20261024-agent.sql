-- 仅用于已有 MySQL 数据库；先备份，在部署新 JAR 前执行一次。
-- 本地默认 H2 内存库直接由 schema.sql 新建，不需要运行本文件。
ALTER TABLE analysis_tasks MODIFY job_id BIGINT NULL;
ALTER TABLE analysis_tasks ADD COLUMN request_json TEXT NULL;
ALTER TABLE analysis_reports MODIFY job_id BIGINT NULL;
