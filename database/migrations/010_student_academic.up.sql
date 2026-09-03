-- 学籍管理模块：班级、学生档案和学业审查快照。
CREATE TABLE tblClass (
    class_id VARCHAR(32) NOT NULL,
    class_name VARCHAR(64) NOT NULL,
    department_name VARCHAR(64),
    major_name VARCHAR(64),
    grade_year INTEGER,
    PRIMARY KEY (class_id)
);

CREATE INDEX idx_tblClass_department ON tblClass(department_name);

CREATE TABLE tblStudent (
    student_id VARCHAR(32) NOT NULL,
    user_id VARCHAR(32),
    student_name VARCHAR(64) NOT NULL,
    gender VARCHAR(8),
    department_name VARCHAR(64),
    major_name VARCHAR(64),
    class_id VARCHAR(32),
    enrollment_year INTEGER,
    status VARCHAR(16) NOT NULL,
    phone VARCHAR(32),
    email VARCHAR(100),
    PRIMARY KEY (student_id)
);

CREATE UNIQUE INDEX uk_tblStudent_user ON tblStudent(user_id);
CREATE INDEX idx_tblStudent_class ON tblStudent(class_id);

CREATE TABLE tblAcademicReview (
    review_id VARCHAR(36) NOT NULL,
    student_id VARCHAR(32) NOT NULL,
    total_earned_credits INTEGER NOT NULL,
    required_earned_credits INTEGER NOT NULL,
    failed_course_count INTEGER NOT NULL,
    retake_course_count INTEGER NOT NULL,
    graduation_ready BIT NOT NULL,
    reviewed_by VARCHAR(32) NOT NULL,
    reviewed_at DATETIME NOT NULL,
    remark VARCHAR(255),
    PRIMARY KEY (review_id)
);

CREATE INDEX idx_tblAcademicReview_student ON tblAcademicReview(student_id);
