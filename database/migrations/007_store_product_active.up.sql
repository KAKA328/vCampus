ALTER TABLE tblProduct ADD COLUMN active BIT;
UPDATE tblProduct SET active = 1 WHERE active IS NULL;
