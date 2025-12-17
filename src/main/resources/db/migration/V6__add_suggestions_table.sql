CREATE TABLE IF NOT EXISTS suggestions
(
    id             VARCHAR(64) PRIMARY KEY,
    title          VARCHAR(200) NOT NULL,
    content        TEXT         NOT NULL,
    student_no     VARCHAR(20)  NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING, REPLIED
    reply_content  TEXT         NULL,
    reply_time     DATETIME     NULL,
    created_at     DATETIME     NOT NULL
);

