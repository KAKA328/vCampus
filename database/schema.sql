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
