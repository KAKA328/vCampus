ALTER TABLE tblUser ADD COLUMN force_password_change BIT;
UPDATE tblUser SET force_password_change = FALSE WHERE force_password_change IS NULL;

ALTER TABLE tblPasswordResetApplication ADD COLUMN reset_reason VARCHAR(120);
ALTER TABLE tblPasswordResetApplication ADD COLUMN contact_info VARCHAR(120);
