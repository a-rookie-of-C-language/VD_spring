
-- Create pending_activities table for activities awaiting review
CREATE TABLE IF NOT EXISTS pending_activities
(
    id                    VARCHAR(64) PRIMARY KEY,
    functionary           VARCHAR(100) NOT NULL,
    name                  VARCHAR(200) NOT NULL,
    type                  VARCHAR(50)  NOT NULL,
    description           TEXT,
    duration              DOUBLE PRECISION NOT NULL,
    end_time              DATETIME     NOT NULL,
    cover_path            VARCHAR(255),
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_by          VARCHAR(100) NOT NULL
);

-- Create pending_participants table for participants of pending activities
CREATE TABLE IF NOT EXISTS pending_participants
(
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    pending_activity_id   VARCHAR(64)  NOT NULL,
    student_no            VARCHAR(20)  NOT NULL,
    UNIQUE KEY uk_pending_activity_student (pending_activity_id, student_no)
);

-- Create pending_attachments table for attachments of pending activities
CREATE TABLE IF NOT EXISTS pending_attachments
(
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    pending_activity_id   VARCHAR(64)  NOT NULL,
    path                  VARCHAR(255) NOT NULL
);

