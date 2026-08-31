CREATE TABLE tblPasswordResetApplication (
    user_id VARCHAR(32) NOT NULL,
    requested_password_hash VARCHAR(255) NOT NULL,
    submitted_at DATETIME NOT NULL,
    status VARCHAR(16) NOT NULL,
    reviewed_by VARCHAR(32),
    reviewed_at DATETIME,
    PRIMARY KEY (user_id)
);

CREATE INDEX idx_tblPasswordResetApplication_status ON tblPasswordResetApplication(status);
CREATE INDEX idx_tblPasswordResetApplication_submitted ON tblPasswordResetApplication(submitted_at);
