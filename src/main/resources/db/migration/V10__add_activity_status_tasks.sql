CREATE TABLE IF NOT EXISTS activity_status_tasks
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id      VARCHAR(64)  NOT NULL,
    activity_id   VARCHAR(64)  NOT NULL,
    target_status VARCHAR(32)  NOT NULL,
    source        VARCHAR(64),
    execute_at    DATETIME     NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempt       INT          NOT NULL DEFAULT 0,
    last_error    VARCHAR(1000),
    next_retry_at DATETIME,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    CONSTRAINT uk_activity_status_tasks_event UNIQUE (event_id)
);

CREATE INDEX idx_activity_status_tasks_status_retry
    ON activity_status_tasks (status, next_retry_at, updated_at);

CREATE INDEX idx_activity_status_tasks_activity
    ON activity_status_tasks (activity_id, target_status);
