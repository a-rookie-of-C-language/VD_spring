-- 批量导入待审核表
-- 用于存储通过Excel批量导入的待审核记录

-- 批量导入记录主表
CREATE TABLE IF NOT EXISTS pending_batch_imports
(
    id                VARCHAR(64) PRIMARY KEY,
    submitted_by      VARCHAR(100) NOT NULL COMMENT '提交人学号',
    original_filename VARCHAR(255) COMMENT '原始文件名',
    total_records     INT NOT NULL DEFAULT 0 COMMENT '总记录数',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/APPROVED/REJECTED',
    reviewed_at       DATETIME COMMENT '审核时间',
    reviewed_by       VARCHAR(100) COMMENT '审核人学号',
    rejected_reason   VARCHAR(500) COMMENT '拒绝理由'
);

-- 批量导入记录详情表（存储每一行的用户和活动信息）
CREATE TABLE IF NOT EXISTS pending_batch_import_records
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id          VARCHAR(64) NOT NULL COMMENT '批次ID',
    username          VARCHAR(80) COMMENT '姓名',
    gender            VARCHAR(10) COMMENT '性别',
    college           VARCHAR(100) COMMENT '学院',
    grade             VARCHAR(20) COMMENT '年级',
    student_no        VARCHAR(20) NOT NULL COMMENT '学号',
    phone             VARCHAR(20) COMMENT '联系方式',
    duration          DOUBLE PRECISION COMMENT '服务时长（小时）',
    activity_name     VARCHAR(200) NOT NULL COMMENT '活动名称（规范化后）',
    original_activity_name VARCHAR(200) COMMENT '原始活动名称',
    user_exists       TINYINT(1) NOT NULL DEFAULT 0 COMMENT '用户是否已存在',
    INDEX idx_batch_id (batch_id),
    INDEX idx_student_no (student_no),
    INDEX idx_activity_name (activity_name)
);

