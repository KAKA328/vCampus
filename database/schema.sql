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

CREATE TABLE tblCourse (
    course_id VARCHAR(32) NOT NULL,
    course_name VARCHAR(100) NOT NULL,
    credits INTEGER NOT NULL,
    capacity INTEGER NOT NULL,
    PRIMARY KEY (course_id)
);

CREATE TABLE tblCourseSelection (
    selection_id VARCHAR(36) NOT NULL,
    student_id VARCHAR(32) NOT NULL,
    course_id VARCHAR(32) NOT NULL,
    selected_at DATETIME NOT NULL,
    PRIMARY KEY (selection_id)
);

CREATE UNIQUE INDEX uk_tblCourseSelection_student_course
    ON tblCourseSelection(student_id, course_id);
CREATE INDEX idx_tblCourseSelection_student ON tblCourseSelection(student_id);
CREATE INDEX idx_tblCourseSelection_course ON tblCourseSelection(course_id);
