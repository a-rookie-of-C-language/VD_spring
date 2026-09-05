ALTER TABLE pending_batch_import_records
    ADD COLUMN validation_status VARCHAR(20) NOT NULL DEFAULT 'VALID' COMMENT 'VALID/INVALID' AFTER user_exists,
    ADD COLUMN validation_error VARCHAR(500) NULL COMMENT 'precheck error message' AFTER validation_status;

