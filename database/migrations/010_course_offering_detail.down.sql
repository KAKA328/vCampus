DROP INDEX idx_tblCourseOffering_term ON tblCourseOffering;
ALTER TABLE tblCourseOffering DROP COLUMN status;
ALTER TABLE tblCourseOffering DROP COLUMN elective_capacity;
ALTER TABLE tblCourseOffering DROP COLUMN required_capacity;
ALTER TABLE tblCourseOffering DROP COLUMN location;
ALTER TABLE tblCourseOffering DROP COLUMN schedule;
ALTER TABLE tblCourseOffering DROP COLUMN term;
