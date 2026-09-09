ALTER TABLE tblCourseMeeting ADD COLUMN start_week INTEGER;
ALTER TABLE tblCourseMeeting ADD COLUMN end_week INTEGER;
UPDATE tblCourseMeeting SET start_week=1 WHERE start_week IS NULL;
UPDATE tblCourseMeeting SET end_week=20 WHERE end_week IS NULL;
