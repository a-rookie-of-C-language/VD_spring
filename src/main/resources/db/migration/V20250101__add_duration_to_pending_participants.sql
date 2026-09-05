-- 为待审核活动参与者表添加时长字段
DELIMITER //
CREATE PROCEDURE add_duration_column_if_missing()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'pending_participants'
          AND COLUMN_NAME = 'duration'
    ) THEN
        ALTER TABLE pending_participants
            ADD COLUMN duration DECIMAL(5,2) DEFAULT NULL COMMENT '参与者志愿时长（小时）';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'activity_participants'
          AND COLUMN_NAME = 'duration'
    ) THEN
        ALTER TABLE activity_participants
            ADD COLUMN duration DECIMAL(5,2) DEFAULT NULL COMMENT '参与者志愿时长（小时）';
    END IF;
END//
DELIMITER ;

CALL add_duration_column_if_missing();
DROP PROCEDURE add_duration_column_if_missing;
