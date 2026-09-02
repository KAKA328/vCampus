CREATE TABLE tblSelectionRound (
    round_id VARCHAR(36) NOT NULL,
    term VARCHAR(32) NOT NULL,
    round_type VARCHAR(16) NOT NULL,
    starts_at DATETIME NOT NULL,
    ends_at DATETIME NOT NULL,
    status VARCHAR(16) NOT NULL,
    PRIMARY KEY (round_id)
);

CREATE INDEX idx_tblSelectionRound_term ON tblSelectionRound(term);
