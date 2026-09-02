-- 为已有课程目录补充启用/停用状态；已有课程默认保持可用。
ALTER TABLE tblCourse ADD COLUMN status VARCHAR(16);
UPDATE tblCourse SET status = 'ACTIVE' WHERE status IS NULL;
