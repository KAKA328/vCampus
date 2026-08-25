CREATE TABLE tblAuditLog (
    log_id VARCHAR(36) NOT NULL,
    actor_user_id VARCHAR(32) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (log_id)
);

CREATE INDEX idx_tblAuditLog_actor ON tblAuditLog(actor_user_id);
CREATE INDEX idx_tblAuditLog_target ON tblAuditLog(target_type, target_id);
