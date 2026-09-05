-- Missing indexes for frequently queried columns

-- suggestions: filtered by student_no and status
CREATE INDEX idx_suggestions_student_no ON suggestions (student_no);
CREATE INDEX idx_suggestions_status ON suggestions (status);

-- activity_attachments: joined by activity_id (N+1 sub-select)
CREATE INDEX idx_activity_attachments_activity_id ON activity_attachments (activity_id);

-- pending_attachments: joined by pending_activity_id (N+1 sub-select)
CREATE INDEX idx_pending_attachments_pending_activity_id ON pending_attachments (pending_activity_id);

-- activities: exact-match lookup by name (batch import)
CREATE INDEX idx_activities_name ON activities (name);
