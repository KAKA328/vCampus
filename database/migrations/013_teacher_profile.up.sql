-- The 010 migration does not create tblTeacher. Create the complete archive here
-- so an installation upgraded only through the numbered migrations is usable.
CREATE TABLE tblTeacher (
    teacher_id VARCHAR(32) NOT NULL,
    user_id VARCHAR(32),
    teacher_name VARCHAR(64) NOT NULL,
    department_name VARCHAR(64),
    title VARCHAR(32),
    active BIT NOT NULL,
    PRIMARY KEY (teacher_id),
    CONSTRAINT uk_tblTeacher_user UNIQUE (user_id)
);
