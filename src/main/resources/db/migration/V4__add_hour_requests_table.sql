-- Create personal_hour_requests table for personal volunteer hour requests
CREATE TABLE IF NOT EXISTS personal_hour_requests
(
    id                    VARCHAR(64) PRIMARY KEY,
    applicant_student_no  VARCHAR(20)  NOT NULL COMMENT '申请人学号',
    name                  VARCHAR(200) NOT NULL COMMENT '活动名称',
    functionary           VARCHAR(100) NOT NULL COMMENT '证明人/负责人姓名',
    type                  VARCHAR(50)  NOT NULL COMMENT '活动类型',
    description           TEXT         COMMENT '活动简述',
    start_time            DATETIME     NOT NULL COMMENT '活动开始时间',
    end_time              DATETIME     NOT NULL COMMENT '活动结束时间',
    duration              DOUBLE PRECISION NOT NULL COMMENT '申请时长',
    status                VARCHAR(30)  NOT NULL DEFAULT 'PENDING' COMMENT '审核状态: PENDING, APPROVED, REJECTED',
    rejected_reason       VARCHAR(500) COMMENT '拒绝理由',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    reviewed_at           DATETIME     COMMENT '审核时间',
    reviewed_by           VARCHAR(20)  COMMENT '审核人学号',
    FOREIGN KEY (applicant_student_no) REFERENCES users(student_no)
);

-- Create personal_hour_request_attachments table for attachments of personal hour requests
CREATE TABLE IF NOT EXISTS personal_hour_request_attachments
(
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id            VARCHAR(64)  NOT NULL,
    path                  VARCHAR(255) NOT NULL COMMENT '附件路径',
    FOREIGN KEY (request_id) REFERENCES personal_hour_requests(id) ON DELETE CASCADE
);

-- Add indexes for better query performance
CREATE INDEX idx_personal_hour_requests_applicant ON personal_hour_requests(applicant_student_no);
CREATE INDEX idx_personal_hour_requests_status ON personal_hour_requests(status);
CREATE INDEX idx_personal_hour_requests_created_at ON personal_hour_requests(created_at);
CREATE INDEX idx_personal_hour_request_attachments_request_id ON personal_hour_request_attachments(request_id);

