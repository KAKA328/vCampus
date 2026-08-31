-- vCampus database baseline. Adapt data types to the installed Access version.
CREATE TABLE tblUser (
    user_id VARCHAR(32) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    role_code VARCHAR(16) NOT NULL,
    active BIT NOT NULL,
    force_password_change BIT NOT NULL,
    created_by VARCHAR(32),
    created_at DATETIME,
    import_batch_id VARCHAR(36),
    PRIMARY KEY (user_id)
);

CREATE UNIQUE INDEX uk_tblUser_display_name ON tblUser(display_name);
CREATE INDEX idx_tblUser_created_by ON tblUser(created_by);
CREATE INDEX idx_tblUser_import_batch ON tblUser(import_batch_id);

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

CREATE TABLE tblPasswordResetApplication (
    user_id VARCHAR(32) NOT NULL,
    requested_password_hash VARCHAR(255) NOT NULL,
    reset_reason VARCHAR(120),
    contact_info VARCHAR(120),
    submitted_at DATETIME NOT NULL,
    status VARCHAR(16) NOT NULL,
    reviewed_by VARCHAR(32),
    reviewed_at DATETIME,
    PRIMARY KEY (user_id)
);

CREATE INDEX idx_tblPasswordResetApplication_status ON tblPasswordResetApplication(status);
CREATE INDEX idx_tblPasswordResetApplication_submitted ON tblPasswordResetApplication(submitted_at);

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

-- 学籍审查与后续教务管理规划表。
-- 账号由系统管理员开户注册或由初始化脚本预置；学生/教师账号应同步创建或绑定对应档案。
-- 学生历史选课、首修/重修和学分通过情况由教务维护或演示数据导入，不由开户注册流程凭空生成。
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

CREATE TABLE tblTeacher (
    teacher_id VARCHAR(32) NOT NULL,
    user_id VARCHAR(32),
    teacher_name VARCHAR(64) NOT NULL,
    department_name VARCHAR(64),
    title VARCHAR(32),
    PRIMARY KEY (teacher_id)
);

CREATE UNIQUE INDEX uk_tblTeacher_user ON tblTeacher(user_id);

CREATE TABLE tblCourseOffering (
    offering_id VARCHAR(36) NOT NULL,
    course_id VARCHAR(32) NOT NULL,
    teacher_id VARCHAR(32),
    semester VARCHAR(32) NOT NULL,
    course_type VARCHAR(16) NOT NULL,
    total_capacity INTEGER NOT NULL,
    major_capacity INTEGER,
    cross_major_capacity INTEGER,
    active BIT NOT NULL,
    PRIMARY KEY (offering_id)
);

CREATE INDEX idx_tblCourseOffering_course ON tblCourseOffering(course_id);
CREATE INDEX idx_tblCourseOffering_teacher ON tblCourseOffering(teacher_id);
CREATE INDEX idx_tblCourseOffering_semester ON tblCourseOffering(semester);

CREATE TABLE tblCourseResult (
    result_id VARCHAR(36) NOT NULL,
    student_id VARCHAR(32) NOT NULL,
    course_id VARCHAR(32) NOT NULL,
    offering_id VARCHAR(36),
    semester VARCHAR(32) NOT NULL,
    attempt_no INTEGER NOT NULL,
    attempt_type VARCHAR(16) NOT NULL,
    score INTEGER,
    passed BIT NOT NULL,
    earned_credits INTEGER NOT NULL,
    recorded_at DATETIME NOT NULL,
    PRIMARY KEY (result_id)
);

CREATE INDEX idx_tblCourseResult_student ON tblCourseResult(student_id);
CREATE INDEX idx_tblCourseResult_course ON tblCourseResult(course_id);

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

CREATE TABLE tblProduct (
    product_id VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    stock INTEGER NOT NULL,
    price DOUBLE NOT NULL,
    description VARCHAR(255),
    category VARCHAR(64) NOT NULL,
    active BIT NOT NULL,
    PRIMARY KEY (product_id)
);

CREATE TABLE tblOrder (
    order_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(32) NOT NULL,
    product_id VARCHAR(32) NOT NULL,
    quantity INTEGER NOT NULL,
    total_price DOUBLE NOT NULL,
    order_date DATETIME NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    unit_price DOUBLE NOT NULL,
    PRIMARY KEY (order_id)
);

CREATE INDEX idx_tblOrder_user ON tblOrder(user_id);
CREATE INDEX idx_tblOrder_product ON tblOrder(product_id);
