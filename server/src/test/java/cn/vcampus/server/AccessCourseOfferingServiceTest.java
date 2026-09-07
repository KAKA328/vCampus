package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.Course;
import cn.vcampus.course.CourseMeeting;
import cn.vcampus.course.CourseOffering;
import cn.vcampus.course.CourseOfferingStatus;
import cn.vcampus.course.CourseSchedule;
import cn.vcampus.course.CourseSelectionRecord;
import cn.vcampus.course.SelectionType;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 验证教学班可真实写入 Access，且不会绕过课程目录校验。 */
class AccessCourseOfferingServiceTest {
    @TempDir
    Path temporaryDirectory;

    private AccessCourseCatalogService catalog;
    private AccessCourseOfferingService service;

    @BeforeEach
    void setUp() throws Exception {
        Path database = temporaryDirectory.resolve("course-offering-test.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
                Statement statement = connection.createStatement()) {
            createCourseTable(statement);
            createCurrentOfferingTable(statement);
            createCurrentMeetingTable(statement);
            createCurrentSelectionRecordTable(statement);
            createTeacherTable(statement);
        }
        catalog = new AccessCourseCatalogService(database);
        catalog.create(new Course("CS101", "程序设计基础", 3));
        service = new AccessCourseOfferingService(database, catalog);
    }

    @Test
    void createsPersistsAndListsOpenOffering() {
        CourseOffering offering = offering("OFFER-001", CourseOfferingStatus.OPEN);

        assertEquals(StatusCode.OK, service.create(offering).getStatus());
        AccessCourseOfferingService restarted = new AccessCourseOfferingService(
                temporaryDirectory.resolve("course-offering-test.accdb"), catalog);
        ServiceResult<List<CourseOffering>> open = restarted.listOpenByCourse("CS101", TERM);

        assertEquals(StatusCode.OK, open.getStatus());
        assertEquals(1, open.getData().size());
        assertEquals("教学楼A201", open.getData().get(0).getLocation());
        assertEquals(30, open.getData().get(0).getRequiredCapacity());
        assertEquals(2, open.getData().get(0).getMeetingSchedule().getMeetings().size());
        assertEquals(DayOfWeek.MONDAY,
                open.getData().get(0).getMeetingSchedule().getMeetings().get(0).getDayOfWeek());
        assertEquals(DayOfWeek.WEDNESDAY,
                open.getData().get(0).getMeetingSchedule().getMeetings().get(1).getDayOfWeek());
    }

    @Test
    void listsOnlyOfferingsAssignedToTeacherInRequestedTerm() {
        service.create(offering("OFFER-001", CourseOfferingStatus.OPEN));
        service.create(offering("OFFER-002", "CS101", "T002", CourseOfferingStatus.OPEN));

        ServiceResult<List<CourseOffering>> assigned = service.listByTeacher("T001", TERM);

        assertEquals(StatusCode.OK, assigned.getStatus());
        assertEquals(1, assigned.getData().size());
        assertEquals("OFFER-001", assigned.getData().get(0).getOfferingId());
    }

    @Test
    void updatesTeacherLocationStatusAndIncreasingCapacity() {
        service.create(offering("OFFER-001", CourseOfferingStatus.DRAFT));

        assertEquals(StatusCode.OK,
                service.updateTeachingInfo("OFFER-001", "T002", "教学楼B302").getStatus());
        assertEquals(StatusCode.OK,
                service.changeCapacities("OFFER-001", 32, 6, 4).getStatus());
        assertEquals(StatusCode.OK,
                service.changeStatus("OFFER-001", CourseOfferingStatus.OPEN).getStatus());
        AccessCourseSelectionRecordService records = new AccessCourseSelectionRecordService(
                temporaryDirectory.resolve("course-offering-test.accdb"), service);
        assertEquals(StatusCode.OK, records.create(new CourseSelectionRecord("RECORD-001", "S001",
                "OFFER-001", "ROUND-001", SelectionType.REQUIRED,
                LocalDateTime.of(2026, 9, 1, 8, 0))).getStatus());

        CourseOffering saved = service.findById("OFFER-001").getData();
        assertEquals("T002", saved.getTeacherId());
        assertEquals("教学楼B302", saved.getLocation());
        assertEquals(42, saved.getTotalCapacity());
        assertEquals(CourseOfferingStatus.OPEN, saved.getStatus());
        assertEquals(StatusCode.CONFLICT,
                service.changeCapacities("OFFER-001", 0, 6, 4).getStatus());
    }

    @Test
    void rejectsDisabledOrUnknownCourseAndDuplicateOffering() {
        assertEquals(StatusCode.OK,
                catalog.changeStatus("CS101", cn.vcampus.course.CourseStatus.DISABLED).getStatus());
        assertEquals(StatusCode.CONFLICT,
                service.create(offering("OFFER-001", CourseOfferingStatus.DRAFT)).getStatus());

        catalog.create(new Course("CS102", "数据结构", 3));
        assertEquals(StatusCode.OK,
                service.create(offering("OFFER-001", "CS102", CourseOfferingStatus.DRAFT)).getStatus());
        assertEquals(StatusCode.CONFLICT,
                service.create(offering("OFFER-001", "CS102", CourseOfferingStatus.DRAFT)).getStatus());
    }

    @Test
    void rejectsUnknownOrInactiveTeacherForCreateAndUpdate() {
        assertEquals(StatusCode.NOT_FOUND,
                service.create(offering("UNKNOWN-TEACHER", "CS101", "T999",
                        CourseOfferingStatus.DRAFT)).getStatus());
        assertEquals(StatusCode.CONFLICT,
                service.create(offering("INACTIVE-TEACHER", "CS101", "T003",
                        CourseOfferingStatus.DRAFT)).getStatus());

        assertEquals(StatusCode.OK,
                service.create(offering("OFFER-001", CourseOfferingStatus.DRAFT)).getStatus());
        assertEquals(StatusCode.NOT_FOUND,
                service.updateTeachingInfo("OFFER-001", "T999", "教学楼B302").getStatus());
        assertEquals(StatusCode.CONFLICT,
                service.updateTeachingInfo("OFFER-001", "T003", "教学楼B302").getStatus());
        assertEquals("T001", service.findById("OFFER-001").getData().getTeacherId());
    }

    private static final String TERM = "2026-2027-1";

    private static CourseOffering offering(String offeringId, CourseOfferingStatus status) {
        return offering(offeringId, "CS101", status);
    }

    private static CourseOffering offering(String offeringId, String courseId,
            CourseOfferingStatus status) {
        return offering(offeringId, courseId, "T001", status);
    }

    private static CourseOffering offering(String offeringId, String courseId, String teacherId,
            CourseOfferingStatus status) {
        return new CourseOffering(offeringId, courseId, TERM, teacherId, "周一第1-2节", "教学楼A201",
                30, 5, 4, status).withMeetingSchedule(new CourseSchedule(Arrays.asList(
                        new CourseMeeting(DayOfWeek.MONDAY, 1, 2, "教学楼A201"),
                        new CourseMeeting(DayOfWeek.WEDNESDAY, 3, 4, "教学楼A201"))));
    }

    private static void createCourseTable(Statement statement) throws Exception {
        statement.execute("CREATE TABLE tblCourse ("
                + "course_id VARCHAR(32) NOT NULL,course_name VARCHAR(100) NOT NULL,"
                + "credits INTEGER NOT NULL,status VARCHAR(16) NOT NULL,"
                + "PRIMARY KEY (course_id))");
    }

    private static void createCurrentOfferingTable(Statement statement) throws Exception {
        statement.execute("CREATE TABLE tblCourseOffering ("
                + "offering_id VARCHAR(36) NOT NULL,course_id VARCHAR(32) NOT NULL,"
                + "teacher_id VARCHAR(32) NOT NULL,term VARCHAR(32) NOT NULL,schedule VARCHAR(128) NOT NULL,"
                + "location VARCHAR(64) NOT NULL,required_capacity INTEGER NOT NULL,"
                + "elective_capacity INTEGER NOT NULL,cross_major_capacity INTEGER NOT NULL,"
                + "status VARCHAR(16) NOT NULL,"
                + "PRIMARY KEY (offering_id))");
    }

    private static void createCurrentSelectionRecordTable(Statement statement) throws Exception {
        statement.execute("CREATE TABLE tblCourseSelection ("
                + "selection_id VARCHAR(36) NOT NULL,student_id VARCHAR(32) NOT NULL,"
                + "offering_id VARCHAR(36) NOT NULL,"
                + "round_id VARCHAR(36) NOT NULL,selection_type VARCHAR(16) NOT NULL,"
                + "selected_at DATETIME NOT NULL,status VARCHAR(16) NOT NULL,dropped_at DATETIME,"
                + "PRIMARY KEY (selection_id))");
        statement.execute("CREATE TABLE tblActiveCourseSelection ("
                + "student_id VARCHAR(32) NOT NULL,offering_id VARCHAR(36) NOT NULL,"
                + "PRIMARY KEY (student_id,offering_id))");
        statement.execute("CREATE TABLE tblCourseOfferingCapacityUsage ("
                + "offering_id VARCHAR(36) NOT NULL,capacity_bucket VARCHAR(16) NOT NULL,"
                + "used_count INTEGER NOT NULL,PRIMARY KEY (offering_id,capacity_bucket))");
    }

    private static void createCurrentMeetingTable(Statement statement) throws Exception {
        statement.execute("CREATE TABLE tblCourseMeeting ("
                + "offering_id VARCHAR(36) NOT NULL,day_of_week INTEGER NOT NULL,"
                + "start_period INTEGER NOT NULL,end_period INTEGER NOT NULL,"
                + "location VARCHAR(64) NOT NULL,"
                + "PRIMARY KEY (offering_id,day_of_week,start_period))");
    }

    private static void createTeacherTable(Statement statement) throws Exception {
        statement.execute("CREATE TABLE tblTeacher ("
                + "teacher_id VARCHAR(32) NOT NULL,user_id VARCHAR(32),"
                + "teacher_name VARCHAR(64) NOT NULL,department_name VARCHAR(64),"
                + "title VARCHAR(32),active BIT NOT NULL,PRIMARY KEY (teacher_id))");
        statement.execute("INSERT INTO tblTeacher(teacher_id,teacher_name,active) "
                + "VALUES ('T001','教师一',1)");
        statement.execute("INSERT INTO tblTeacher(teacher_id,teacher_name,active) "
                + "VALUES ('T002','教师二',1)");
        statement.execute("INSERT INTO tblTeacher(teacher_id,teacher_name,active) "
                + "VALUES ('T003','离职教师',0)");
    }

}
