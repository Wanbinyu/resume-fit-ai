CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(120) NOT NULL,
    display_name VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS resumes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    source_file_name VARCHAR(255),
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS job_descriptions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS analysis_tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    resume_id BIGINT NOT NULL,
    job_id BIGINT NULL,
    request_json TEXT,
    input_snapshot_json MEDIUMTEXT,
    status VARCHAR(20) NOT NULL,
    progress INT NOT NULL DEFAULT 0,
    message VARCHAR(255),
    result_json MEDIUMTEXT,
    error_message VARCHAR(500),
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_usage_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT,
    provider VARCHAR(40),
    model_name VARCHAR(100),
    input_tokens INT NOT NULL DEFAULT 0,
    output_tokens INT NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS resume_access_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    resume_id BIGINT NOT NULL,
    version_id BIGINT NULL,
    viewer_user_id BIGINT NULL,
    source VARCHAR(40) NOT NULL DEFAULT 'unknown',
    accessed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_resume_access_logs_resume_id ON resume_access_logs (resume_id);
CREATE INDEX IF NOT EXISTS idx_resume_access_logs_version_id ON resume_access_logs (version_id);
CREATE INDEX IF NOT EXISTS idx_resume_access_logs_accessed_at ON resume_access_logs (accessed_at);

CREATE TABLE IF NOT EXISTS resume_versions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    resume_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    title VARCHAR(160) NOT NULL,
    source VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    target_job_id BIGINT NULL,
    analysis_task_id BIGINT NULL,
    content TEXT NOT NULL,
    share_token VARCHAR(80) NULL UNIQUE,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_resume_versions_resume_no ON resume_versions (resume_id, version_no);
CREATE INDEX IF NOT EXISTS idx_resume_versions_user_id ON resume_versions (user_id);

CREATE TABLE IF NOT EXISTS analysis_reports (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    resume_id BIGINT NOT NULL,
    job_id BIGINT NULL,
    score INT NOT NULL DEFAULT 0,
    report_json MEDIUMTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_analysis_reports_resume_id ON analysis_reports (resume_id);
CREATE INDEX IF NOT EXISTS idx_analysis_reports_user_id ON analysis_reports (user_id);

-- Career records are private to an account. Run once after backing up an existing database.
CREATE TABLE IF NOT EXISTS career_profiles (
    user_id BIGINT PRIMARY KEY,
    content_json TEXT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS career_companies (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    industry VARCHAR(100) NOT NULL DEFAULT '',
    website VARCHAR(1000) NOT NULL DEFAULT '',
    evidence_json TEXT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS career_jobs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    company_name VARCHAR(160) NOT NULL,
    company_id BIGINT NULL,
    location VARCHAR(80) NOT NULL DEFAULT '',
    salary_min INT NULL,
    salary_max INT NULL,
    description TEXT NOT NULL,
    source_url VARCHAR(1000) NOT NULL DEFAULT '',
    collected_at DATE NOT NULL,
    expires_at DATE NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS career_applications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    stage VARCHAR(20) NOT NULL,
    notes TEXT NOT NULL,
    next_action_at TIMESTAMP NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_career_applications_user_job UNIQUE (user_id, job_id)
);

CREATE TABLE IF NOT EXISTS career_application_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    from_stage VARCHAR(20) NULL,
    to_stage VARCHAR(20) NOT NULL,
    action VARCHAR(30) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_career_companies_owner ON career_companies (user_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_career_jobs_owner ON career_jobs (user_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_career_applications_owner ON career_applications (user_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_career_application_events_owner ON career_application_events (user_id, application_id, id);
