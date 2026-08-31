ALTER TABLE tblProduct ADD COLUMN active BIT;
UPDATE tblProduct SET active = 1 WHERE active IS NULL;
ALTER TABLE tblProduct ALTER COLUMN active BIT NOT NULL;
