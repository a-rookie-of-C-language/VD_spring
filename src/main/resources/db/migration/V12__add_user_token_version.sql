ALTER TABLE users
    ADD COLUMN token_version INT NOT NULL DEFAULT 0 COMMENT 'JWT revoke version' AFTER password;
CREATE INDEX idx_activities_start_time_id ON activities (start_time, id);

