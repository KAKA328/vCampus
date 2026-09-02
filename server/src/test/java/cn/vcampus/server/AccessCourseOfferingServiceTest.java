package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.Course;
import cn.vcampus.course.CourseOffering;
import cn.vcampus.course.CourseOfferingStatus;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
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

        CourseOffering saved = service.findById("OFFER-001").getData();
        assertEquals("T002", saved.getTeacherId());
        assertEquals("教学楼B302", saved.getLocation());
        assertEquals(42, saved.getTotalCapacity());
        assertEquals(CourseOfferingStatus.OPEN, saved.getStatus());
        assertEquals(StatusCode.CONFLICT,
                service.changeCapacities("OFFER-001", 31, 6, 4).getStatus());
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
    void migrationMakesLegacyOfferingDraftUntilAcademicStaffCompletesIt() throws Exception {
        Path database = temporaryDirectory.resolve("legacy-course-offering-test.accdb");
        try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
                Statement statement = connection.createStatement()) {
            createCourseTable(statement);
            statement.execute("INSERT INTO tblCourse(course_id,course_name,credits,capacity,status) "
                    + "VALUES ('CS101','程序设计基础',3,0,'ACTIVE')");
            statement.execute("CREATE TABLE tblCourseOffering ("
                    + "offering_id VARCHAR(36) NOT NULL,course_id VARCHAR(32) NOT NULL,"
                    + "teacher_id VARCHAR(32),semester VARCHAR(32) NOT NULL,"
                    + "course_type VARCHAR(16) NOT NULL,total_capacity INTEGER NOT NULL,"
                    + "major_capacity INTEGER,cross_major_capacity INTEGER,active BIT NOT NULL,"
                    + "PRIMARY KEY (offering_id))");
            statement.execute("INSERT INTO tblCourseOffering(offering_id,course_id,teacher_id,"
                    + "semester,course_type,total_capacity,major_capacity,cross_major_capacity,active) "
                    + "VALUES ('OLD-001','CS101','T001','2026-2027-1','必修',40,35,5,1)");
            applyOfferingMigration(statement);
        }

        AccessCourseCatalogService migratedCatalog = new AccessCourseCatalogService(database);
        AccessCourseOfferingService migrated = new AccessCourseOfferingService(database,
                migratedCatalog);
        CourseOffering saved = migrated.findById("OLD-001").getData();

        assertEquals(TERM, saved.getTerm());
        assertEquals(35, saved.getRequiredCapacity());
        assertEquals(0, saved.getElectiveCapacity());
        assertEquals(CourseOfferingStatus.DRAFT, saved.getStatus());
        assertEquals(0, migrated.listOpenByCourse("CS101", TERM).getData().size());
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
                + "credits INTEGER NOT NULL,capacity INTEGER NOT NULL,status VARCHAR(16) NOT NULL,"
                + "PRIMARY KEY (course_id))");
    }

    private static void createCurrentOfferingTable(Statement statement) throws Exception {
        statement.execute("CREATE TABLE tblCourseOffering ("
                + "offering_id VARCHAR(36) NOT NULL,course_id VARCHAR(32) NOT NULL,"
                + "teacher_id VARCHAR(32),semester VARCHAR(32) NOT NULL,"
                + "course_type VARCHAR(16) NOT NULL,total_capacity INTEGER NOT NULL,"
                + "major_capacity INTEGER,cross_major_capacity INTEGER,active BIT NOT NULL,"
                + "term VARCHAR(32) NOT NULL,schedule VARCHAR(128) NOT NULL,"
                + "location VARCHAR(64) NOT NULL,required_capacity INTEGER NOT NULL,"
                + "elective_capacity INTEGER NOT NULL,status VARCHAR(16) NOT NULL,"
                + "PRIMARY KEY (offering_id))");
    }

    private static void applyOfferingMigration(Statement statement) throws Exception {
        statement.execute("ALTER TABLE tblCourseOffering ADD COLUMN term VARCHAR(32)");
        statement.execute("ALTER TABLE tblCourseOffering ADD COLUMN schedule VARCHAR(128)");
        statement.execute("ALTER TABLE tblCourseOffering ADD COLUMN location VARCHAR(64)");
        statement.execute("ALTER TABLE tblCourseOffering ADD COLUMN required_capacity INTEGER");
        statement.execute("ALTER TABLE tblCourseOffering ADD COLUMN elective_capacity INTEGER");
        statement.execute("ALTER TABLE tblCourseOffering ADD COLUMN status VARCHAR(16)");
        statement.execute("UPDATE tblCourseOffering SET term=semester WHERE term IS NULL");
        statement.execute("UPDATE tblCourseOffering SET teacher_id='UNASSIGNED' WHERE teacher_id IS NULL");
        statement.execute("UPDATE tblCourseOffering SET schedule='待安排' WHERE schedule IS NULL");
        statement.execute("UPDATE tblCourseOffering SET location='待安排' WHERE location IS NULL");
        statement.execute("UPDATE tblCourseOffering SET cross_major_capacity=0 "
                + "WHERE cross_major_capacity IS NULL");
        statement.execute("UPDATE tblCourseOffering SET required_capacity=major_capacity "
                + "WHERE major_capacity IS NOT NULL");
        statement.execute("UPDATE tblCourseOffering SET required_capacity=total_capacity-cross_major_capacity "
                + "WHERE major_capacity IS NULL");
        statement.execute("UPDATE tblCourseOffering SET elective_capacity=0 "
                + "WHERE elective_capacity IS NULL");
        statement.execute("UPDATE tblCourseOffering SET status='DRAFT' WHERE status IS NULL");
    }
}
