CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE job_applications (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    company           VARCHAR(150)  NOT NULL,
    role              VARCHAR(150)  NOT NULL,
    location          VARCHAR(150),
    job_url           VARCHAR(500),
    salary_range      VARCHAR(100),
    status            VARCHAR(20)   NOT NULL,
    applied_date      DATE,
    follow_up_date    DATE,
    reminder_sent_at  TIMESTAMPTZ,
    skills            VARCHAR(1000),
    notes             TEXT,
    job_description   TEXT,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_application_status
        CHECK (status IN ('WISHLIST', 'APPLIED', 'INTERVIEW', 'OFFER', 'REJECTED'))
);

CREATE INDEX idx_applications_user_status ON job_applications (user_id, status);
CREATE INDEX idx_applications_due_reminders ON job_applications (follow_up_date)
    WHERE reminder_sent_at IS NULL AND follow_up_date IS NOT NULL;

CREATE TABLE status_history (
    id             BIGSERIAL PRIMARY KEY,
    application_id BIGINT      NOT NULL REFERENCES job_applications (id) ON DELETE CASCADE,
    from_status    VARCHAR(20),
    to_status      VARCHAR(20) NOT NULL,
    changed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_status_history_application ON status_history (application_id, changed_at);

CREATE TABLE notifications (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    application_id BIGINT       REFERENCES job_applications (id) ON DELETE CASCADE,
    message        VARCHAR(500) NOT NULL,
    is_read        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user ON notifications (user_id, is_read, created_at DESC);
