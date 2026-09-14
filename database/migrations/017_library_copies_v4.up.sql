-- Library V4: stop the server and migrate a COPY of the database before switching.
-- Cross-table consistency is checked by LibraryCopyIntegrity and shared transactions (UCanAccess 4.0.4).
-- Data backfill is performed by LibraryCopyMigration, not by resetting seed.sql.
CREATE TABLE tblBookCopy (
    copy_id VARCHAR(40) NOT NULL,
    book_id VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    PRIMARY KEY (copy_id)
);
-- Do not use CREATE INDEX here: bundled UCanAccess 4.0.4 does not support it.
CREATE TABLE tblLibraryLoanSnapshot (
    record_id VARCHAR(40) NOT NULL,
    book_title VARCHAR(120) NOT NULL,
    copy_id VARCHAR(40),
    historical_backfill BOOLEAN NOT NULL,
    PRIMARY KEY (record_id)
);
CREATE TABLE tblLibraryCopyCatalog (
    book_id VARCHAR(32) NOT NULL,
    PRIMARY KEY (book_id)
);
CREATE TABLE tblLibraryBookEdit (
    edit_id VARCHAR(36) NOT NULL,
    book_id VARCHAR(32) NOT NULL,
    operator_id VARCHAR(32) NOT NULL,
    edited_at DATETIME NOT NULL,
    before_details MEMO NOT NULL,
    after_details MEMO NOT NULL,
    PRIMARY KEY (edit_id)
);
