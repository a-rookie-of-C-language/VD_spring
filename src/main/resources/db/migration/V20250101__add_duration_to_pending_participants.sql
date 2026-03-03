-- 为待审核活动参与者表添加时长字段
ALTER TABLE pending_activity_participants
ADD COLUMN IF NOT EXISTS duration DECIMAL(5,2) DEFAULT NULL COMMENT '参与者志愿时长（小时）';

-- 为正式活动参与者表添加时长字段（如果需要）
ALTER TABLE activity_participants
ADD COLUMN IF NOT EXISTS duration DECIMAL(5,2) DEFAULT NULL COMMENT '参与者志愿时长（小时）';

