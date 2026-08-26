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
