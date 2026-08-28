ALTER TABLE tblUser ADD COLUMN created_by VARCHAR(32);
ALTER TABLE tblUser ADD COLUMN created_at DATETIME;
ALTER TABLE tblUser ADD COLUMN import_batch_id VARCHAR(36);

CREATE INDEX idx_tblUser_created_by ON tblUser(created_by);
CREATE INDEX idx_tblUser_import_batch ON tblUser(import_batch_id);
