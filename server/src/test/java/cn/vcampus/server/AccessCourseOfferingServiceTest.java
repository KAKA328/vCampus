package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.Course;
import cn.vcampus.course.CourseOffering;
import cn.vcampus.course.CourseOfferingStatus;
import cn.vcampus.course.CourseSelectionRecord;
import cn.vcampus.course.SelectionType;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
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
            createCurrentSelectionRecordTable(statement);
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

    private static final String TERM = "2026-2027-1";

    private static CourseOffering offering(String offeringId, CourseOfferingStatus status) {
        return offering(offeringId, "CS101", status);
    }

    private static CourseOffering offering(String offeringId, String courseId,
            CourseOfferingStatus status) {
        return new CourseOffering(offeringId, courseId, TERM, "T001", "周一第1-2节", "教学楼A201",
                30, 5, 4, status);
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
    }

}
