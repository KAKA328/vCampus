ALTER TABLE tblPasswordResetApplication DROP COLUMN contact_info;
ALTER TABLE tblPasswordResetApplication DROP COLUMN reset_reason;

ALTER TABLE tblUser DROP COLUMN force_password_change;
