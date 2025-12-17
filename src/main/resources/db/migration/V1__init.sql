create table if not exists users
(
    student_no  varchar(20) primary key,
    username    varchar(80)      null,
    password    varchar(200)     null,
    role        varchar(20)      null,
    created_at  datetime         null,
    total_hours double precision null
);


CREATE TABLE IF NOT EXISTS activities
(
    id                    VARCHAR(64) PRIMARY KEY,
    functionary           VARCHAR(100) NOT NULL,
    name                  VARCHAR(200) NOT NULL,
    type                  VARCHAR(50)  NOT NULL,
    description           TEXT,
    enrollment_start_time DATETIME     NOT NULL,
    enrollment_end_time   DATETIME     NOT NULL,
    start_time            DATETIME     NOT NULL,
    end_time              DATETIME     NOT NULL,
    cover                 VARCHAR(255),
    max_participants      INT          NOT NULL DEFAULT 0,
    status                VARCHAR(50)  NOT NULL,
    is_full               TINYINT(1)   NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS activity_attachments
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id VARCHAR(64)  NOT NULL,
    path        VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS activity_participants
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id VARCHAR(64)  NOT NULL,
    student_no  VARCHAR(20)  NOT NULL,
    UNIQUE KEY uk_activity_student (activity_id, student_no)
);


insert into vd.activities (id, functionary, name, type, description, enrollment_start_time, enrollment_end_time, start_time, expected_end_time, cover_path, status, is_full, max_participants, duration, end_time, imported, rejected_reason)
values  ('860ea40d-8442-4573-a80d-3af73688757a', '12323020421', '鸿蒙开发', 'COMMUNITY_SERVICE', '开发鸿蒙应用', '2025-11-26 00:00:00', '2025-11-26 17:30:00', '2025-11-29 00:00:00', '2025-11-30 00:00:00', '/covers/f099362a-c6b9-4f22-989a-38a7d17940df.png', 'ActivityEnded', false, 10, 0.5, null, null, null),
        ('99ee1eba-bdc0-4bf1-a731-7de2bf7bfe76', '12323020421', '12345', 'CULTURE_SERVICE', '测试', '2025-11-27 08:00:00', '2025-11-29 17:17:08', '2025-11-30 00:00:00', '2025-12-03 00:00:00', '/covers/73f4b2ee-4644-4204-b0f6-dca3d8bd5a5b.png', 'ActivityEnded', false, 13, 2, null, null, null),
        ('a2d9e052-c86c-11f0-86cf-6afcd3b45678', '12323020420', '活动01 社区服务', 'COMMUNITY_SERVICE', '社区清洁与环境美化', '2025-11-24 21:02:16', '2025-11-25 21:02:16', '2025-11-26 21:02:16', '2025-11-27 21:02:16', '/images/covers/act01.jpg', 'ActivityEnded', false, 30, 2, null, null, null),
        ('a2dbb293-c86c-11f0-86cf-6afcd3b45678', '12323020420', '活动02 文化服务', 'CULTURE_SERVICE', '博物馆志愿讲解支持', '2025-11-25 21:02:16', '2025-11-26 21:02:16', '2025-11-27 21:02:16', '2025-11-28 21:02:16', '/images/covers/act02.jpg', 'ActivityEnded', false, 40, 1.5, null, null, null),
        ('a2dd5146-c86c-11f0-86cf-6afcd3b45678', '12323020420', '活动03 应急救援', 'EMERGENCY_RESCUE', '自然灾害应急演练支持', '2025-11-26 21:02:16', '2025-11-27 21:02:16', '2025-11-28 21:02:16', '2025-11-29 21:02:16', '/images/covers/act03.jpg', 'ActivityEnded', false, 50, 1, null, null, null),
        ('a2def164-c86c-11f0-86cf-6afcd3b45678', '12323020420', '活动04 动物保护', 'ANIMAL_PROTECTION', '动物收容所志愿服务', '2025-11-27 21:02:16', '2025-11-28 21:02:16', '2025-11-29 21:02:16', '2025-11-30 21:02:16', '/images/covers/act04.jpg', 'ActivityEnded', false, 25, 0.5, null, null, null),
        ('a2e08e08-c86c-11f0-86cf-6afcd3b45678', '12323020420', '活动05 扶贫助困', 'POVERTY_ASSISTANCE', '社区爱心助学与生活照料', '2025-11-28 21:02:16', '2025-11-29 21:02:16', '2025-11-30 21:02:16', '2025-12-01 21:02:16', '/images/covers/act05.jpg', 'ActivityEnded', false, 60, 3, null, null, null),
        ('a2e25917-c86c-11f0-86cf-6afcd3b45678', '12323020420', '活动06 扶老助残', 'ELDERLY_DISABLED_ASSISTANCE', '养老院生活照料与健康支持', '2025-11-29 21:02:16', '2025-11-30 21:02:16', '2025-12-01 21:02:16', '2025-12-02 21:02:16', '/images/covers/act06.jpg', 'ActivityEnded', false, 35, 2.5, null, null, null),
        ('a2e3dcb0-c86c-11f0-86cf-6afcd3b45678', '12323020420', '活动07 慰病助医', 'MEDICAL_ASSISTANCE', '医院探视与心理慰藉', '2025-11-30 21:02:16', '2025-12-01 21:02:16', '2025-12-02 21:02:16', '2025-12-03 21:02:16', '/images/covers/act07.jpg', 'ActivityEnded', false, 20, 4, null, null, null),
        ('a2e55709-c86c-11f0-86cf-6afcd3b45678', '12323020420', '活动08 救孤助学', 'ORPHAN_EDUCATION_ASSISTANCE', '学业辅导与亲情陪伴', '2025-12-01 21:02:16', '2025-12-02 21:02:16', '2025-12-03 21:02:16', '2025-12-04 21:02:16', '/images/covers/act08.jpg', 'ActivityEnded', false, 45, 1.6, null, null, null),
        ('a2e6e2eb-c86c-11f0-86cf-6afcd3b45678', '12323020420', '活动09 社区服务', 'COMMUNITY_SERVICE', '社区文化活动支持', '2025-12-02 21:02:16', '2025-12-03 21:02:16', '2025-12-04 21:02:16', '2025-12-05 21:02:16', '/images/covers/act09.jpg', 'ActivityEnded', false, 28, 4, null, null, null),
        ('a2e84324-c86c-11f0-86cf-6afcd3b45678', '12323020420', '活动10 文化服务', 'CULTURE_SERVICE', '文化中心活动支持', '2025-12-03 21:02:16', '2025-12-04 21:02:16', '2025-12-05 21:02:16', '2025-12-06 21:02:16', '/images/covers/act10.jpg', 'ActivityEnded', false, 32, 2, null, null, null),
        ('a2ea0e94-c86c-11f0-86cf-6afcd3b45678', '12323020420', '活动11 应急救援', 'EMERGENCY_RESCUE', '灾害救援物资整理', '2025-12-04 21:02:16', '2025-12-05 21:02:16', '2025-12-06 21:02:16', '2025-12-07 21:02:16', '/images/covers/act11.jpg', 'ActivityEnded', false, 55, 1, null, null, null),
        ('a2ebdfb8-c86c-11f0-86cf-6afcd3b45678', '12323020420', '活动12 动物保护', 'ANIMAL_PROTECTION', '动物保护宣传活动', '2025-12-05 21:02:16', '2025-12-06 21:02:16', '2025-12-07 21:02:16', '2025-12-08 21:02:16', '/images/covers/act12.jpg', 'ActivityEnded', false, 24, 2, null, null, null),
        ('a2ed7829-c86c-11f0-86cf-6afcd3b45678', '12323020420', '活动13 扶贫助困', 'POVERTY_ASSISTANCE', '社区慰问与物资发放', '2025-12-06 21:02:16', '2025-12-07 21:02:16', '2025-12-08 21:02:16', '2025-12-09 21:02:16', '/images/covers/act13.jpg', 'ActivityEnded', false, 48, 1, null, null, null),
        ('a2ef0530-c86c-11f0-86cf-6afcd3b45678', '12323020420', '活动14 扶老助残', 'ELDERLY_DISABLED_ASSISTANCE', '知识普及与健康讲座', '2025-12-07 21:02:16', '2025-12-08 21:02:16', '2025-12-09 21:02:16', '2025-12-10 21:02:16', '/images/covers/act14.jpg', 'ActivityEnded', false, 36, 2, null, null, null),
        ('a2f1fe8c-c86c-11f0-86cf-6afcd3b45678', '12323020420', '活动15 慰病助医', 'MEDICAL_ASSISTANCE', '情感慰藉与康复陪伴', '2025-12-08 21:02:16', '2025-12-09 21:02:16', '2025-12-10 21:02:16', '2025-12-11 21:02:16', '/images/covers/act15.jpg', 'ActivityEnded', false, 22, 1, null, null, null),
        ('a2f3d056-c86c-11f0-86cf-6afcd3b45678', '12323020420', '活动16 救孤助学', 'ORPHAN_EDUCATION_ASSISTANCE', '学习辅导提升能力', '2025-12-09 21:02:16', '2025-12-10 21:02:16', '2025-12-11 21:02:16', '2025-12-12 21:02:16', '/images/covers/act16.jpg', 'ActivityEnded', false, 42, 3, null, null, null),
        ('a2f5d0d0-c86c-11f0-86cf-6afcd3b45678', '12323020420', '活动17 社区服务', 'COMMUNITY_SERVICE', '环境美化与秩序维护', '2025-12-10 21:02:16', '2025-12-11 21:02:16', '2025-12-12 21:02:16', '2025-12-13 21:02:16', '/images/covers/act17.jpg', 'ActivityEnded', false, 33, 2, null, null, null),
        ('a2f86ae9-c86c-11f0-86cf-6afcd3b45678', '12323020420', '活动18 文化服务', 'CULTURE_SERVICE', '文化活动组织与支持', '2025-12-11 21:02:16', '2025-12-12 21:02:16', '2025-12-13 21:02:16', '2025-12-14 21:02:16', '/images/covers/act18.jpg', 'ActivityEnded', false, 38, 1, null, null, null),
        ('a2fa6715-c86c-11f0-86cf-6afcd3b45678', '12323020420', '活动19 应急救援', 'EMERGENCY_RESCUE', '应急演练与安全宣传', '2025-12-12 21:02:16', '2025-12-13 21:02:16', '2025-12-14 21:02:16', '2025-12-15 21:02:16', '/images/covers/act19.jpg', 'ActivityStarted', false, 52, 2, null, null, null),
        ('a2fc3352-c86c-11f0-86cf-6afcd3b45678', '12323020420', '活动20 动物保护', 'ANIMAL_PROTECTION', '动物保护组织志愿服务', '2025-12-13 21:02:16', '2025-12-14 21:02:16', '2025-12-15 21:02:16', '2025-12-16 21:02:16', '/images/covers/act20.jpg', 'ActivityEnded', false, 26, 1, null, null, null),
        ('dacf373d-1167-425a-8466-8ccce14b3bbf', '12323020421', '测试', 'COMMUNITY_SERVICE', '测试', '2025-12-08 00:00:00', '2025-12-09 00:00:00', '2025-12-16 00:00:00', '2025-12-19 00:00:00', '/covers/c0fbe91d-c272-41a5-9dd0-e3d9837facd7.jpg', 'FailReview', false, 10, 0.5, null, null, null);

insert into vd.activity_participants (id, activity_id, student_no)
values  (7, '860ea40d-8442-4573-a80d-3af73688757a', '12323020406'),
        (6, '860ea40d-8442-4573-a80d-3af73688757a', '12323020421'),
        (11, '99ee1eba-bdc0-4bf1-a731-7de2bf7bfe76', '12323020334'),
        (9, '99ee1eba-bdc0-4bf1-a731-7de2bf7bfe76', '12323020421'),
        (12, 'a2ed7829-c86c-11f0-86cf-6afcd3b45678', '12323020406'),
        (15, 'a2ed7829-c86c-11f0-86cf-6afcd3b45678', '12323020420'),
        (16, 'a2f86ae9-c86c-11f0-86cf-6afcd3b45678', '12323020406'),
        (13, 'dacf373d-1167-425a-8466-8ccce14b3bbf', '12323020421');

insert into vd.users (student_no, username, password, role, created_at, total_hours, clazz, grade, college)
values  ('12323020334', '高永旗', 'arookieofc', 'user', '2025-11-27 19:03:55', 100, '123230203', '2023', '两江人工智能学院'),
        ('12323020406', '罗正', 'arookieofc', 'user', '2025-11-24 16:18:26', 0, '123230204', '2023', '两江人工智能学院'),
        ('12323020420', '黄智哲', 'arookieofc', 'superAdmin', '2025-11-25 21:49:45', 0, '123230204', '2023', '两江人工智能学院'),
        ('12323020421', '贺文杰', 'arookieofc', 'functionary', '2025-11-25 21:50:28', 0, '123230204', '2023', '两江人工智能学院'),
        ('12323020422', '龚文杰', 'arookieofc', 'admin', '2025-11-25 21:50:57', 0, '123230204', '2023', '两江人工智能学院');
