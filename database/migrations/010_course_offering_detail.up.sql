-- 将早期教学班表升级为支持三类容量、地点和状态的结构。
ALTER TABLE tblCourseOffering ADD COLUMN term VARCHAR(32);
ALTER TABLE tblCourseOffering ADD COLUMN schedule VARCHAR(128);
ALTER TABLE tblCourseOffering ADD COLUMN location VARCHAR(64);
ALTER TABLE tblCourseOffering ADD COLUMN required_capacity INTEGER;
ALTER TABLE tblCourseOffering ADD COLUMN elective_capacity INTEGER;
ALTER TABLE tblCourseOffering ADD COLUMN status VARCHAR(16);

UPDATE tblCourseOffering SET term = semester WHERE term IS NULL;
UPDATE tblCourseOffering SET teacher_id = 'UNASSIGNED' WHERE teacher_id IS NULL;
UPDATE tblCourseOffering SET schedule = '待安排' WHERE schedule IS NULL;
UPDATE tblCourseOffering SET location = '待安排' WHERE location IS NULL;
UPDATE tblCourseOffering SET cross_major_capacity = 0 WHERE cross_major_capacity IS NULL;
UPDATE tblCourseOffering SET required_capacity = major_capacity WHERE major_capacity IS NOT NULL;
UPDATE tblCourseOffering SET required_capacity = total_capacity - cross_major_capacity
    WHERE major_capacity IS NULL;
UPDATE tblCourseOffering SET elective_capacity = 0 WHERE elective_capacity IS NULL;
-- 无法从旧表可靠推断完整教学安排，迁移后的班次一律先由教务人员核对再开放。
UPDATE tblCourseOffering SET status = 'DRAFT' WHERE status IS NULL;

CREATE INDEX idx_tblCourseOffering_term ON tblCourseOffering(term);
