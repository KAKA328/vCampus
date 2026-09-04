ALTER TABLE tblTeacher ADD COLUMN active BIT;
UPDATE tblTeacher SET active = 1 WHERE active IS NULL;
ALTER TABLE tblTeacher ALTER COLUMN active BIT NOT NULL;
