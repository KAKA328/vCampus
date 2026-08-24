-- Demo-only accounts. Replace password_hash values with hashes from the chosen implementation.
INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active)
VALUES ('demo-admin', 'REPLACE_WITH_HASH', 'Demo Administrator', 'ADMIN', 1);

INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active)
VALUES ('demo-student', 'REPLACE_WITH_HASH', 'Demo Student', 'STUDENT', 1);
