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

}
