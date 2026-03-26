EXPLAIN FORMAT=TRADITIONAL
SELECT
    u.grade AS name,
    COUNT(u.student_no) AS userCount,
    COALESCE(SUM(apu.activity_count), 0) AS activityCount,
    COALESCE(SUM(u.total_hours), 0) AS totalHours,
    COALESCE(AVG(u.total_hours), 0) AS averageHours
FROM users u
LEFT JOIN (
    SELECT student_no, COUNT(DISTINCT activity_id) AS activity_count
    FROM activity_participants
    GROUP BY student_no
) apu ON u.student_no = apu.student_no
WHERE u.grade IS NOT NULL AND u.grade != ''
GROUP BY u.grade
ORDER BY totalHours DESC;

EXPLAIN FORMAT=TRADITIONAL
SELECT COUNT(ap.id)
FROM activity_participants ap
INNER JOIN users u ON ap.student_no = u.student_no
WHERE u.college = '两江人工智能学院'
  AND u.grade = '2023'
  AND u.clazz = '123230204';

EXPLAIN FORMAT=TRADITIONAL
SELECT
    u.student_no AS studentNo,
    u.username AS name,
    u.college AS college,
    u.grade AS grade,
    u.clazz AS clazz,
    COALESCE(u.total_hours, 0) AS totalDuration,
    COALESCE(apu.activity_count, 0) AS activityCount
FROM users u
LEFT JOIN (
    SELECT student_no, COUNT(DISTINCT activity_id) AS activity_count
    FROM activity_participants
    GROUP BY student_no
) apu ON u.student_no = apu.student_no
WHERE u.college = '两江人工智能学院'
  AND u.grade = '2023'
ORDER BY totalDuration DESC
LIMIT 10 OFFSET 0;
