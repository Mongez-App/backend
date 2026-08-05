-- Add team_id to courses table
ALTER TABLE courses ADD COLUMN team_id VARCHAR(64);
CREATE INDEX idx_courses_team_id ON courses(team_id);