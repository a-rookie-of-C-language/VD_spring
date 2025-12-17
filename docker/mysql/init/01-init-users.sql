-- 初始化数据库用户和权限
CREATE USER IF NOT EXISTS 'vd_user'@'%' IDENTIFIED BY 'su201314';
GRANT ALL PRIVILEGES ON VD.* TO 'vd_user'@'%';
FLUSH PRIVILEGES;

-- 设置时区
SET time_zone = '+08:00';

-- 插入测试用户数据
INSERT IGNORE INTO users (student_no, username, password, role, created_at, total_hours, clazz, grade, college) VALUES
('12323020420', '管理员', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKXgwkOPczk1XqTz4.1gUOaQRfYy', 'admin', NOW(), 0.0, '计科2302', '2023级', '计算机学院'),
('12323020421', '负责人', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKXgwkOPczk1XqTz4.1gUOaQRfYy', 'functionary', NOW(), 8.5, '计科2302', '2023级', '计算机学院'),
('20230001', '张三', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKXgwkOPczk1XqTz4.1gUOaQRfYy', 'user', NOW(), 15.5, '计科2301', '2023级', '计算机学院'),
('20230002', '李四', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKXgwkOPczk1XqTz4.1gUOaQRfYy', 'user', NOW(), 12.0, '计科2301', '2023级', '计算机学院'),
('20230003', '王五', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKXgwkOPczk1XqTz4.1gUOaQRfYy', 'user', NOW(), 8.0, '软工2301', '2023级', '计算机学院'),
('20230004', '赵六', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKXgwkOPczk1XqTz4.1gUOaQRfYy', 'user', NOW(), 5.5, '软工2301', '2023级', '计算机学院'),
('20220001', '陈七', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKXgwkOPczk1XqTz4.1gUOaQRfYy', 'user', NOW(), 20.0, '计科2201', '2022级', '计算机学院'),
('20220002', '孙八', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKXgwkOPczk1XqTz4.1gUOaQRfYy', 'user', NOW(), 18.5, '计科2201', '2022级', '计算机学院');

-- 密码是: 123456 (BCrypt加密后的值)
