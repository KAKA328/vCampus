-- Demo-only accounts. Replace password_hash values with hashes from the chosen implementation.
INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active)
VALUES ('demo-admin', 'REPLACE_WITH_HASH', 'Demo Administrator', 'ADMIN', 1);

INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active)
VALUES ('demo-student', 'REPLACE_WITH_HASH', 'Demo Student', 'STUDENT', 1);

INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active)
VALUES ('demo-teacher', 'REPLACE_WITH_HASH', 'Demo Teacher', 'TEACHER', 1);

-- Library demo catalog. Keep in sync with InMemoryLibraryService.seedCatalog().
INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, total_copies, available_copies, location)
VALUES ('B001', 'Java核心技术（卷I）', 'Cay S. Horstmann', '9787115547392', '计算机', '机械工业出版社', 3, 3, 'A-01');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, total_copies, available_copies, location)
VALUES ('B002', '算法导论', 'Thomas H. Cormen', '9787111407010', '计算机', '机械工业出版社', 2, 2, 'A-02');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, total_copies, available_copies, location)
VALUES ('B003', '红楼梦', '曹雪芹', '9787020002207', '文学', '人民文学出版社', 2, 2, 'B-01');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, total_copies, available_copies, location)
VALUES ('B004', '三体', '刘慈欣', '9787536692930', '科幻', '重庆出版社', 4, 4, 'B-02');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, total_copies, available_copies, location)
VALUES ('B005', '高等数学（第七版）', '同济大学数学系', '9787040396638', '教材', '高等教育出版社', 5, 5, 'C-01');

-- Demo borrow records: one single order and one batch order.
INSERT INTO tblBorrowRecord(record_id, order_id, user_id, book_id, borrow_date, due_date, return_date, status)
VALUES ('BR1', 'BO1', 'demo-student', 'B001', '2026-08-25', '2026-09-24', '2026-08-28', 'RETURNED');

INSERT INTO tblBorrowRecord(record_id, order_id, user_id, book_id, borrow_date, due_date, return_date, status)
VALUES ('BR2', 'BO2', 'demo-student', 'B003', '2026-08-29', '2026-09-28', NULL, 'BORROWED');

INSERT INTO tblBorrowRecord(record_id, order_id, user_id, book_id, borrow_date, due_date, return_date, status)
VALUES ('BR3', 'BO2', 'demo-student', 'B004', '2026-08-29', '2026-09-28', NULL, 'BORROWED');

INSERT INTO tblBorrowRecord(record_id, order_id, user_id, book_id, borrow_date, due_date, return_date, status)
VALUES ('BR4', 'BO3', 'demo-teacher', 'B002', '2026-08-30', '2026-09-29', NULL, 'BORROWED');
