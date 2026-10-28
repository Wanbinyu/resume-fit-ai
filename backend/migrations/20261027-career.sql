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

CREATE INDEX idx_career_companies_owner ON career_companies (user_id, updated_at);
CREATE INDEX idx_career_jobs_owner ON career_jobs (user_id, updated_at);
CREATE INDEX idx_career_applications_owner ON career_applications (user_id, updated_at);
CREATE INDEX idx_career_application_events_owner ON career_application_events (user_id, application_id, id);
