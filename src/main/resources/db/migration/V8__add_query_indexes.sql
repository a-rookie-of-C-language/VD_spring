CREATE INDEX idx_activities_status_start_time ON activities (status, start_time);
CREATE INDEX idx_activities_functionary_start_time ON activities (functionary, start_time);
CREATE INDEX idx_activities_type_start_time ON activities (type, start_time);
CREATE INDEX idx_activities_status_end_time ON activities (status, end_time);

CREATE INDEX idx_activity_participants_student_no ON activity_participants (student_no);

CREATE INDEX idx_pending_activities_submit_created ON pending_activities (submitted_by, created_at);
CREATE INDEX idx_pending_activities_type_created ON pending_activities (type, created_at);

CREATE INDEX idx_personal_hour_requests_app_status_created
    ON personal_hour_requests (applicant_student_no, status, created_at);

CREATE INDEX idx_users_college_grade_clazz ON users (college, grade, clazz);
CREATE INDEX idx_users_total_hours ON users (total_hours);
