-- Add clazz, grade, college fields to users table
ALTER TABLE users ADD COLUMN clazz VARCHAR(50) NULL COMMENT '班级';
ALTER TABLE users ADD COLUMN grade VARCHAR(20) NULL COMMENT '年级';
ALTER TABLE users ADD COLUMN college VARCHAR(100) NULL COMMENT '学院';

-- Add indexes for better query performance
CREATE INDEX idx_users_grade ON users(grade);
CREATE INDEX idx_users_college ON users(college);
CREATE INDEX idx_users_clazz ON users(clazz);

