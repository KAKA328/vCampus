-- Apply once after migration 014. Back up the database before upgrading.
-- Existing books, loans, balances and wallet transactions are preserved.
-- Original-price snapshots: one bill per lost borrowing record; no wallet balance is stored here.
CREATE TABLE tblLibraryCompensation (
    compensation_id VARCHAR(36) NOT NULL,
    record_id VARCHAR(40) NOT NULL,
    user_id VARCHAR(32) NOT NULL,
    book_id VARCHAR(32) NOT NULL,
    book_title VARCHAR(120) NOT NULL,
    amount_cents BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_by VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    paid_at DATETIME,
    PRIMARY KEY (compensation_id),
    CONSTRAINT uk_LibraryCompensation_record UNIQUE (record_id)
);
