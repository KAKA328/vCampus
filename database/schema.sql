-- vCampus database baseline. Adapt data types to the installed Access version.
CREATE TABLE tblUser (
    user_id VARCHAR(32) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    role_code VARCHAR(16) NOT NULL,
    active BIT NOT NULL,
    PRIMARY KEY (user_id)
);

CREATE UNIQUE INDEX uk_tblUser_display_name ON tblUser(display_name);

-- ---------------------------------------------------------------------------
-- Library module: book catalog and borrowing ledger
-- ---------------------------------------------------------------------------

-- One row = one book title in the catalog.
-- available_copies is a fast-read cache; the service layer keeps it consistent
-- with the active rows in tblBorrowRecord.
CREATE TABLE tblBook (
    book_id          VARCHAR(32)  NOT NULL,
    title            VARCHAR(128) NOT NULL,
    author           VARCHAR(64)  NOT NULL,
    isbn             VARCHAR(32)  NULL,
    category         VARCHAR(32)  NULL,
    publisher        VARCHAR(64)  NULL,
    total_copies     INTEGER      NOT NULL,
    available_copies INTEGER      NOT NULL,
    location         VARCHAR(32)  NULL,
    PRIMARY KEY (book_id)
);

CREATE INDEX idx_tblBook_category ON tblBook(category);
CREATE INDEX idx_tblBook_title ON tblBook(title);

-- One row = one physical borrowing of one book by one user.
-- order_id groups a batch request together; record_id identifies one row.
-- Dates are stored as TEXT (YYYY-MM-DD) for Access compatibility.
CREATE TABLE tblBorrowRecord (
    record_id   VARCHAR(32) NOT NULL,
    order_id    VARCHAR(32) NOT NULL,
    user_id     VARCHAR(32) NOT NULL,
    book_id     VARCHAR(32) NOT NULL,
    borrow_date VARCHAR(10) NOT NULL,
    due_date    VARCHAR(10) NOT NULL,
    return_date VARCHAR(10) NULL,
    status      VARCHAR(16) NOT NULL,
    PRIMARY KEY (record_id)
);

CREATE INDEX idx_tblBorrowRecord_user ON tblBorrowRecord(user_id, status);
CREATE INDEX idx_tblBorrowRecord_order ON tblBorrowRecord(order_id);

-- Optional helper table for renewing a due date without rewriting history.
CREATE TABLE tblBorrowRenew (
    renew_id     INTEGER      NOT NULL,
    record_id    VARCHAR(32)  NOT NULL,
    renew_date   VARCHAR(10)  NOT NULL,
    old_due_date VARCHAR(10)  NOT NULL,
    new_due_date VARCHAR(10)  NOT NULL,
    PRIMARY KEY (renew_id)
);
