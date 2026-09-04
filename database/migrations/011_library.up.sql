CREATE TABLE tblBook (
    book_id VARCHAR(32) NOT NULL,
    title VARCHAR(120) NOT NULL,
    author VARCHAR(100) NOT NULL,
    isbn VARCHAR(32),
    category VARCHAR(64),
    publisher VARCHAR(100),
    total_copies INTEGER NOT NULL,
    available_copies INTEGER NOT NULL,
    location VARCHAR(64),
    PRIMARY KEY (book_id)
);

CREATE INDEX idx_tblBook_title ON tblBook(title);
CREATE INDEX idx_tblBook_category ON tblBook(category);

CREATE TABLE tblBorrowRecord (
    record_id VARCHAR(40) NOT NULL,
    order_id VARCHAR(40) NOT NULL,
    user_id VARCHAR(32) NOT NULL,
    book_id VARCHAR(32) NOT NULL,
    borrow_date DATETIME NOT NULL,
    due_date DATETIME NOT NULL,
    return_date DATETIME,
    status VARCHAR(16) NOT NULL,
    PRIMARY KEY (record_id)
);

CREATE INDEX idx_tblBorrowRecord_user ON tblBorrowRecord(user_id, status);
CREATE INDEX idx_tblBorrowRecord_book ON tblBorrowRecord(book_id, status);
CREATE INDEX idx_tblBorrowRecord_order ON tblBorrowRecord(order_id);

CREATE TABLE tblBorrowRenew (
    renew_id VARCHAR(40) NOT NULL,
    record_id VARCHAR(40) NOT NULL,
    previous_due_date DATETIME NOT NULL,
    renewed_due_date DATETIME NOT NULL,
    renewed_at DATETIME NOT NULL,
    renewed_by VARCHAR(32) NOT NULL,
    PRIMARY KEY (renew_id)
);

CREATE INDEX idx_tblBorrowRenew_record ON tblBorrowRenew(record_id);
