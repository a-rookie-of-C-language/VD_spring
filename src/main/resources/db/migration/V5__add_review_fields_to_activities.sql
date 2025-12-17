-- Add review-related fields to activities table
ALTER TABLE activities
    ADD COLUMN reviewed_at DATETIME COMMENT '审核时间',
    ADD COLUMN reviewed_by VARCHAR(20) COMMENT '审核人学号';

ALTER TABLE pending_activities
    ADD COLUMN reviewed_at DATETIME COMMENT '审核时间',
    ADD COLUMN reviewed_by VARCHAR(20) COMMENT '审核人学号',
    ADD COLUMN rejected_reason VARCHAR(500) COMMENT '拒绝理由',
    ADD COLUMN status VARCHAR(30) DEFAULT 'PENDING' COMMENT '审核状态: PENDING, APPROVED, REJECTED';

