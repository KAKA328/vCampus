package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.Course;
import cn.vcampus.course.CourseStatus;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 验证课程目录的创建、停用和修改会真实写入 Access。 */
class AccessCourseCatalogServiceTest {
    @TempDir
    Path temporaryDirectory;

    private AccessCourseCatalogService service;

    @BeforeEach
    void setUp() throws Exception {
        Path database = temporaryDirectory.resolve("course-catalog-test.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblCourse ("
                    + "course_id VARCHAR(32) NOT NULL,"
                    + "course_name VARCHAR(100) NOT NULL,"
                    + "credits INTEGER NOT NULL,"
                    + "status VARCHAR(16) NOT NULL,"
                    + "PRIMARY KEY (course_id))");
            statement.execute("CREATE TABLE tblCourseOffering ("
                    + "offering_id VARCHAR(36) NOT NULL,course_id VARCHAR(32) NOT NULL)");
            statement.execute("CREATE TABLE tblTrainingPlanCourse ("
                    + "plan_id VARCHAR(36) NOT NULL,course_id VARCHAR(32) NOT NULL)");
            statement.execute("CREATE TABLE tblCourseResult ("
                    + "result_id VARCHAR(36) NOT NULL,course_id VARCHAR(32) NOT NULL)");
        }
        service = new AccessCourseCatalogService(database);
    }

    @Test
    void createsPersistsAndListsCourses() {
        Course course = new Course("CS101", "程序设计基础", 3);

        assertEquals(StatusCode.OK, service.create(course).getStatus());
        AccessCourseCatalogService restarted = new AccessCourseCatalogService(
                temporaryDirectory.resolve("course-catalog-test.accdb"));
        ServiceResult<List<Course>> listed = restarted.listActive();

        assertEquals(StatusCode.OK, listed.getStatus());
        assertEquals(1, listed.getData().size());
        assertEquals("CS101", listed.getData().get(0).getCourseId());
        assertEquals(CourseStatus.ACTIVE, listed.getData().get(0).getStatus());
    }

    @Test
    void updatesDetailsAndKeepsDisabledCourseForHistory() {
        service.create(new Course("CS101", "程序设计基础", 3));

        assertEquals(StatusCode.OK,
                service.updateDetails("CS101", "Java 程序设计", 4).getStatus());
        assertEquals(StatusCode.OK,
                service.changeStatus("CS101", CourseStatus.DISABLED).getStatus());

        ServiceResult<Course> saved = service.findById("CS101");
        assertEquals("Java 程序设计", saved.getData().getName());
        assertEquals(4, saved.getData().getCredits());
        assertEquals(CourseStatus.DISABLED, saved.getData().getStatus());
        assertEquals(StatusCode.CONFLICT, service.findActiveById("CS101").getStatus());
        assertEquals(0, service.listActive().getData().size());
    }

    @Test
    void rejectsDuplicateAndInvalidRequests() {
        service.create(new Course("CS101", "程序设计基础", 3));

        assertEquals(StatusCode.CONFLICT,
                service.create(new Course("CS101", "另一门课程", 2)).getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.create(null).getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.updateDetails("CS101", "", 3).getStatus());
        assertEquals(StatusCode.NOT_FOUND,
                service.changeStatus("UNKNOWN", CourseStatus.ACTIVE).getStatus());
    }

    @Test
    void renamesCourseAndKeepsReferencedCourseIdsConsistent() throws Exception {
        service.create(new Course("CS101", "程序设计基础", 3));
        Path database = temporaryDirectory.resolve("course-catalog-test.accdb");
        try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";immediatelyReleaseResources=true"); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO tblCourseOffering(offering_id,course_id) VALUES('O001','CS101')");
            statement.execute("INSERT INTO tblTrainingPlanCourse(plan_id,course_id) VALUES('P001','CS101')");
            statement.execute("INSERT INTO tblCourseResult(result_id,course_id) VALUES('R001','CS101')");
        }

        Course updated = new Course("CS201", "程序设计进阶", 4)
                .withStatus(CourseStatus.DISABLED);
        assertEquals(StatusCode.OK, service.updateDetails("CS101", updated).getStatus());
        assertEquals(StatusCode.NOT_FOUND, service.findById("CS101").getStatus());
        assertEquals(CourseStatus.DISABLED, service.findById("CS201").getData().getStatus());

        try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";immediatelyReleaseResources=true"); Statement statement = connection.createStatement()) {
            assertEquals("CS201", value(statement, "tblCourseOffering", "course_id"));
            assertEquals("CS201", value(statement, "tblTrainingPlanCourse", "course_id"));
            assertEquals("CS201", value(statement, "tblCourseResult", "course_id"));
        }
    }

    private static String value(Statement statement, String table, String column) throws Exception {
        try (java.sql.ResultSet results = statement.executeQuery("SELECT " + column + " FROM " + table)) {
            return results.next() ? results.getString(1) : null;
        }
    }

}
