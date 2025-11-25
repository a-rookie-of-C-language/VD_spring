create table if not exists users
(
    student_no  varchar(20) primary key,
    username    varchar(80)      null,
    password    varchar(200)     null,
    role        varchar(20)      null,
    created_at  datetime         null,
    total_hours double precision null
);

-- Seed activities: 20 random-like rows for front-end integration testing
INSERT INTO activities (id, functionary, name, type, description,
                        enrollment_start_time, enrollment_end_time,
                        start_time, end_time, cover, max_participants, status, is_full)
VALUES (UUID(), 'arookieofc', '活动01 社区服务', 'COMMUNITY_SERVICE', '社区清洁与环境美化',
        DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY),
        DATE_ADD(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 4 DAY), '/images/covers/act01.jpg', 30, 'EnrollmentStarted', 0);

INSERT INTO activities (id, functionary, name, type, description,
                        enrollment_start_time, enrollment_end_time,
                        start_time, end_time, cover, max_participants, status, is_full)
VALUES (UUID(), 'arookieofc', '活动02 文化服务', 'CULTURE_SERVICE', '博物馆志愿讲解支持',
        DATE_ADD(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 3 DAY),
        DATE_ADD(NOW(), INTERVAL 4 DAY), DATE_ADD(NOW(), INTERVAL 5 DAY), '/images/covers/act02.jpg', 40, 'EnrollmentNotStart', 0);

INSERT INTO activities (id, functionary, name, type, description,
                        enrollment_start_time, enrollment_end_time,
                        start_time, end_time, cover, max_participants, status, is_full)
VALUES (UUID(), 'arookieofc', '活动03 应急救援', 'EMERGENCY_RESCUE', '自然灾害应急演练支持',
        DATE_ADD(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 4 DAY),
        DATE_ADD(NOW(), INTERVAL 5 DAY), DATE_ADD(NOW(), INTERVAL 6 DAY), '/images/covers/act03.jpg', 50, 'EnrollmentStarted', 0);

INSERT INTO activities (id, functionary, name, type, description,
                        enrollment_start_time, enrollment_end_time,
                        start_time, end_time, cover, max_participants, status, is_full)
VALUES (UUID(), 'arookieofc', '活动04 动物保护', 'ANIMAL_PROTECTION', '动物收容所志愿服务',
        DATE_ADD(NOW(), INTERVAL 4 DAY), DATE_ADD(NOW(), INTERVAL 5 DAY),
        DATE_ADD(NOW(), INTERVAL 6 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), '/images/covers/act04.jpg', 25, 'EnrollmentStarted', 0);

INSERT INTO activities (id, functionary, name, type, description,
                        enrollment_start_time, enrollment_end_time,
                        start_time, end_time, cover, max_participants, status, is_full)
VALUES (UUID(), 'arookieofc', '活动05 扶贫助困', 'POVERTY_ASSISTANCE', '社区爱心助学与生活照料',
        DATE_ADD(NOW(), INTERVAL 5 DAY), DATE_ADD(NOW(), INTERVAL 6 DAY),
        DATE_ADD(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 8 DAY), '/images/covers/act05.jpg', 60, 'EnrollmentEnded', 0);

INSERT INTO activities (id, functionary, name, type, description,
                        enrollment_start_time, enrollment_end_time,
                        start_time, end_time, cover, max_participants, status, is_full)
VALUES (UUID(), 'arookieofc', '活动06 扶老助残', 'ELDERLY_DISABLED_ASSISTANCE', '养老院生活照料与健康支持',
        DATE_ADD(NOW(), INTERVAL 6 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY),
        DATE_ADD(NOW(), INTERVAL 8 DAY), DATE_ADD(NOW(), INTERVAL 9 DAY), '/images/covers/act06.jpg', 35, 'EnrollmentStarted', 0);

INSERT INTO activities (id, functionary, name, type, description,
                        enrollment_start_time, enrollment_end_time,
                        start_time, end_time, cover, max_participants, status, is_full)
VALUES (UUID(), 'arookieofc', '活动07 慰病助医', 'MEDICAL_ASSISTANCE', '医院探视与心理慰藉',
        DATE_ADD(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 8 DAY),
        DATE_ADD(NOW(), INTERVAL 9 DAY), DATE_ADD(NOW(), INTERVAL 10 DAY), '/images/covers/act07.jpg', 20, 'ActivityStarted', 0);

INSERT INTO activities (id, functionary, name, type, description,
                        enrollment_start_time, enrollment_end_time,
                        start_time, end_time, cover, max_participants, status, is_full)
VALUES (UUID(), 'arookieofc', '活动08 救孤助学', 'ORPHAN_EDUCATION_ASSISTANCE', '学业辅导与亲情陪伴',
        DATE_ADD(NOW(), INTERVAL 8 DAY), DATE_ADD(NOW(), INTERVAL 9 DAY),
        DATE_ADD(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 11 DAY), '/images/covers/act08.jpg', 45, 'EnrollmentStarted', 0);

INSERT INTO activities (id, functionary, name, type, description,
                        enrollment_start_time, enrollment_end_time,
                        start_time, end_time, cover, max_participants, status, is_full)
VALUES (UUID(), 'arookieofc', '活动09 社区服务', 'COMMUNITY_SERVICE', '社区文化活动支持',
        DATE_ADD(NOW(), INTERVAL 9 DAY), DATE_ADD(NOW(), INTERVAL 10 DAY),
        DATE_ADD(NOW(), INTERVAL 11 DAY), DATE_ADD(NOW(), INTERVAL 12 DAY), '/images/covers/act09.jpg', 28, 'EnrollmentStarted', 0);

INSERT INTO activities (id, functionary, name, type, description,
                        enrollment_start_time, enrollment_end_time,
                        start_time, end_time, cover, max_participants, status, is_full)
VALUES (UUID(), 'arookieofc', '活动10 文化服务', 'CULTURE_SERVICE', '文化中心活动支持',
        DATE_ADD(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 11 DAY),
        DATE_ADD(NOW(), INTERVAL 12 DAY), DATE_ADD(NOW(), INTERVAL 13 DAY), '/images/covers/act10.jpg', 32, 'EnrollmentStarted', 0);

INSERT INTO activities (id, functionary, name, type, description,
                        enrollment_start_time, enrollment_end_time,
                        start_time, end_time, cover, max_participants, status, is_full)
VALUES (UUID(), 'arookieofc', '活动11 应急救援', 'EMERGENCY_RESCUE', '灾害救援物资整理',
        DATE_ADD(NOW(), INTERVAL 11 DAY), DATE_ADD(NOW(), INTERVAL 12 DAY),
        DATE_ADD(NOW(), INTERVAL 13 DAY), DATE_ADD(NOW(), INTERVAL 14 DAY), '/images/covers/act11.jpg', 55, 'EnrollmentStarted', 0);

INSERT INTO activities (id, functionary, name, type, description,
                        enrollment_start_time, enrollment_end_time,
                        start_time, end_time, cover, max_participants, status, is_full)
VALUES (UUID(), 'arookieofc', '活动12 动物保护', 'ANIMAL_PROTECTION', '动物保护宣传活动',
        DATE_ADD(NOW(), INTERVAL 12 DAY), DATE_ADD(NOW(), INTERVAL 13 DAY),
        DATE_ADD(NOW(), INTERVAL 14 DAY), DATE_ADD(NOW(), INTERVAL 15 DAY), '/images/covers/act12.jpg', 24, 'EnrollmentNotStart', 0);

INSERT INTO activities (id, functionary, name, type, description,
                        enrollment_start_time, enrollment_end_time,
                        start_time, end_time, cover, max_participants, status, is_full)
VALUES (UUID(), 'arookieofc', '活动13 扶贫助困', 'POVERTY_ASSISTANCE', '社区慰问与物资发放',
        DATE_ADD(NOW(), INTERVAL 13 DAY), DATE_ADD(NOW(), INTERVAL 14 DAY),
        DATE_ADD(NOW(), INTERVAL 15 DAY), DATE_ADD(NOW(), INTERVAL 16 DAY), '/images/covers/act13.jpg', 48, 'EnrollmentStarted', 0);

INSERT INTO activities (id, functionary, name, type, description,
                        enrollment_start_time, enrollment_end_time,
                        start_time, end_time, cover, max_participants, status, is_full)
VALUES (UUID(), 'arookieofc', '活动14 扶老助残', 'ELDERLY_DISABLED_ASSISTANCE', '知识普及与健康讲座',
        DATE_ADD(NOW(), INTERVAL 14 DAY), DATE_ADD(NOW(), INTERVAL 15 DAY),
        DATE_ADD(NOW(), INTERVAL 16 DAY), DATE_ADD(NOW(), INTERVAL 17 DAY), '/images/covers/act14.jpg', 36, 'EnrollmentStarted', 0);

INSERT INTO activities (id, functionary, name, type, description,
                        enrollment_start_time, enrollment_end_time,
                        start_time, end_time, cover, max_participants, status, is_full)
VALUES (UUID(), 'arookieofc', '活动15 慰病助医', 'MEDICAL_ASSISTANCE', '情感慰藉与康复陪伴',
        DATE_ADD(NOW(), INTERVAL 15 DAY), DATE_ADD(NOW(), INTERVAL 16 DAY),
        DATE_ADD(NOW(), INTERVAL 17 DAY), DATE_ADD(NOW(), INTERVAL 18 DAY), '/images/covers/act15.jpg', 22, 'EnrollmentEnded', 0);

INSERT INTO activities (id, functionary, name, type, description,
                        enrollment_start_time, enrollment_end_time,
                        start_time, end_time, cover, max_participants, status, is_full)
VALUES (UUID(), 'arookieofc', '活动16 救孤助学', 'ORPHAN_EDUCATION_ASSISTANCE', '学习辅导提升能力',
        DATE_ADD(NOW(), INTERVAL 16 DAY), DATE_ADD(NOW(), INTERVAL 17 DAY),
        DATE_ADD(NOW(), INTERVAL 18 DAY), DATE_ADD(NOW(), INTERVAL 19 DAY), '/images/covers/act16.jpg', 42, 'EnrollmentStarted', 0);

INSERT INTO activities (id, functionary, name, type, description,
                        enrollment_start_time, enrollment_end_time,
                        start_time, end_time, cover, max_participants, status, is_full)
VALUES (UUID(), 'arookieofc', '活动17 社区服务', 'COMMUNITY_SERVICE', '环境美化与秩序维护',
        DATE_ADD(NOW(), INTERVAL 17 DAY), DATE_ADD(NOW(), INTERVAL 18 DAY),
        DATE_ADD(NOW(), INTERVAL 19 DAY), DATE_ADD(NOW(), INTERVAL 20 DAY), '/images/covers/act17.jpg', 33, 'ActivityStarted', 0);

INSERT INTO activities (id, functionary, name, type, description,
                        enrollment_start_time, enrollment_end_time,
                        start_time, end_time, cover, max_participants, status, is_full)
VALUES (UUID(), 'arookieofc', '活动18 文化服务', 'CULTURE_SERVICE', '文化活动组织与支持',
        DATE_ADD(NOW(), INTERVAL 18 DAY), DATE_ADD(NOW(), INTERVAL 19 DAY),
        DATE_ADD(NOW(), INTERVAL 20 DAY), DATE_ADD(NOW(), INTERVAL 21 DAY), '/images/covers/act18.jpg', 38, 'EnrollmentStarted', 0);

INSERT INTO activities (id, functionary, name, type, description,
                        enrollment_start_time, enrollment_end_time,
                        start_time, end_time, cover, max_participants, status, is_full)
VALUES (UUID(), 'arookieofc', '活动19 应急救援', 'EMERGENCY_RESCUE', '应急演练与安全宣传',
        DATE_ADD(NOW(), INTERVAL 19 DAY), DATE_ADD(NOW(), INTERVAL 20 DAY),
        DATE_ADD(NOW(), INTERVAL 21 DAY), DATE_ADD(NOW(), INTERVAL 22 DAY), '/images/covers/act19.jpg', 52, 'EnrollmentStarted', 0);

INSERT INTO activities (id, functionary, name, type, description,
                        enrollment_start_time, enrollment_end_time,
                        start_time, end_time, cover, max_participants, status, is_full)
VALUES (UUID(), 'arookieofc', '活动20 动物保护', 'ANIMAL_PROTECTION', '动物保护组织志愿服务',
        DATE_ADD(NOW(), INTERVAL 20 DAY), DATE_ADD(NOW(), INTERVAL 21 DAY),
        DATE_ADD(NOW(), INTERVAL 22 DAY), DATE_ADD(NOW(), INTERVAL 23 DAY), '/images/covers/act20.jpg', 26, 'ActivityEnded', 0);

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
