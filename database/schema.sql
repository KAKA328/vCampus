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
    PRIMARY KEY (user_id),
    CONSTRAINT uk_tblUser_display_name UNIQUE (display_name)
);

CREATE TABLE tblAuditLog (
    log_id VARCHAR(36) NOT NULL,
    actor_user_id VARCHAR(32) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (log_id)
);

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

CREATE TABLE tblCourse (
    course_id VARCHAR(32) NOT NULL,
    course_name VARCHAR(100) NOT NULL,
    credits INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    PRIMARY KEY (course_id)
);

CREATE TABLE tblCourseSelection (
    selection_id VARCHAR(36) NOT NULL,
    student_id VARCHAR(32) NOT NULL,
    offering_id VARCHAR(36) NOT NULL,
    round_id VARCHAR(36) NOT NULL,
    selection_type VARCHAR(16) NOT NULL,
    selected_at DATETIME NOT NULL,
    status VARCHAR(16) NOT NULL,
    dropped_at DATETIME,
    PRIMARY KEY (selection_id)
);

-- 学籍审查与后续教务管理规划表。
-- 账号由系统管理员开户注册或由初始化脚本预置；学生/教师账号应同步创建或绑定对应档案。
-- 学生历史选课、首修/重修和学分通过情况由教务维护或演示数据导入，不由开户注册流程凭空生成。
CREATE TABLE tblClass (
    class_id VARCHAR(32) NOT NULL,
    class_name VARCHAR(64) NOT NULL,
    department_name VARCHAR(64),
    major_name VARCHAR(64),
    grade_year INTEGER,
    PRIMARY KEY (class_id)
);

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
    PRIMARY KEY (student_id),
    CONSTRAINT uk_tblStudent_user UNIQUE (user_id)
);

CREATE TABLE tblTeacher (
    teacher_id VARCHAR(32) NOT NULL,
    user_id VARCHAR(32),
    teacher_name VARCHAR(64) NOT NULL,
    department_name VARCHAR(64),
    title VARCHAR(32),
    PRIMARY KEY (teacher_id),
    CONSTRAINT uk_tblTeacher_user UNIQUE (user_id)
);

CREATE TABLE tblCourseOffering (
    offering_id VARCHAR(36) NOT NULL,
    course_id VARCHAR(32) NOT NULL,
    teacher_id VARCHAR(32) NOT NULL,
    term VARCHAR(32) NOT NULL,
    schedule VARCHAR(128) NOT NULL,
    location VARCHAR(64) NOT NULL,
    required_capacity INTEGER NOT NULL,
    elective_capacity INTEGER NOT NULL,
    cross_major_capacity INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    PRIMARY KEY (offering_id)
);

-- 一个教学班可包含多次上课安排，用于选课时的时间冲突检测。
CREATE TABLE tblCourseMeeting (
    offering_id VARCHAR(36) NOT NULL,
    day_of_week INTEGER NOT NULL,
    start_period INTEGER NOT NULL,
    end_period INTEGER NOT NULL,
    location VARCHAR(64) NOT NULL,
    PRIMARY KEY (offering_id, day_of_week, start_period)
);

CREATE TABLE tblTrainingPlan (
    plan_id VARCHAR(36) NOT NULL,
    major_name VARCHAR(64) NOT NULL,
    enrollment_year INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    PRIMARY KEY (plan_id)
);

CREATE TABLE tblTrainingPlanCourse (
    plan_id VARCHAR(36) NOT NULL,
    course_id VARCHAR(32) NOT NULL,
    recommended_term INTEGER NOT NULL,
    selection_type VARCHAR(16) NOT NULL,
    cross_major_allowed BIT NOT NULL,
    PRIMARY KEY (plan_id, course_id)
);

-- 教务人员维护的选课轮次。每个学期至多配置一个首修轮次和一个重修轮次。
CREATE TABLE tblSelectionRound (
    round_id VARCHAR(36) NOT NULL,
    term VARCHAR(32) NOT NULL,
    round_type VARCHAR(16) NOT NULL,
    starts_at DATETIME NOT NULL,
    ends_at DATETIME NOT NULL,
    status VARCHAR(16) NOT NULL,
    PRIMARY KEY (round_id)
);

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

CREATE TABLE tblCartItem (
    cart_item_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(32) NOT NULL,
    product_id VARCHAR(32) NOT NULL,
    quantity INTEGER NOT NULL,
    added_at DATETIME NOT NULL,
    PRIMARY KEY (cart_item_id),
    CONSTRAINT uk_tblCartItem_user_product UNIQUE (user_id, product_id)
);

-- 校园钱包账户：余额以「分」为单位存 BIGINT，user_id 为主键（懒创建 upsert 依赖主键去重，不另建唯一索引以规避 UCanAccess 4.0.4 的 CREATE UNIQUE INDEX 限制）。
CREATE TABLE tblBankAccount (
    user_id VARCHAR(32) NOT NULL,
    balance_cents BIGINT NOT NULL,
    PRIMARY KEY (user_id)
);
