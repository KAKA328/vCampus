DROP INDEX idx_tblUser_import_batch ON tblUser;
DROP INDEX idx_tblUser_created_by ON tblUser;

ALTER TABLE tblUser DROP COLUMN import_batch_id;
ALTER TABLE tblUser DROP COLUMN created_at;
ALTER TABLE tblUser DROP COLUMN created_by;
