CREATE TABLE IF NOT EXISTS volunteer_hour_grant_records
(
    id          VARCHAR(64) PRIMARY KEY,
    student_no  VARCHAR(20)   NOT NULL,
    source_type VARCHAR(32)   NOT NULL,
    source_id   VARCHAR(64)   NOT NULL,
    source_name VARCHAR(255),
    duration    DOUBLE        NOT NULL,
    granted_at  DATETIME      NOT NULL,
    operator    VARCHAR(20),
    remark      VARCHAR(255),
    CONSTRAINT uk_volunteer_hour_grant_unique UNIQUE (student_no, source_type, source_id)
);

CREATE INDEX idx_volunteer_hour_grant_source
    ON volunteer_hour_grant_records (source_type, source_id);
