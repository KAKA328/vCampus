package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.StatusCode;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 使用临时 Access 数据库验证选课数据可以真实保存。 */
class AccessCourseSelectionServiceTest {
    @TempDir
    Path temporaryDirectory;

    private AccessCourseSelectionService service;

    @BeforeEach
    void setUp() throws Exception {
        Path database = temporaryDirectory.resolve("course-selection-test.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:ucanaccess://" + database
                        + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblCourse ("
                    + "course_id VARCHAR(32) NOT NULL,"
                    + "course_name VARCHAR(100) NOT NULL,"
                    + "credits INTEGER NOT NULL,"
                    + "capacity INTEGER NOT NULL,"
                    + "PRIMARY KEY (course_id))");
            statement.execute("CREATE TABLE tblCourseSelection ("
                    + "selection_id VARCHAR(36) NOT NULL,"
                    + "student_id VARCHAR(32) NOT NULL,"
                    + "course_id VARCHAR(32) NOT NULL,"
                    + "selected_at DATETIME NOT NULL,"
                    + "PRIMARY KEY (selection_id))");
            insertCourse(connection, "JAVA101", "Java 程序设计", 3, 1);
            insertCourse(connection, "DB101", "数据库原理", 3, 2);
        }
        service = new AccessCourseSelectionService(database);
    }

    @Test
    void listsCoursesAndPersistsSuccessfulSelection() {
        assertEquals(2, service.listCourses().getData().size());
        assertEquals(StatusCode.OK, service.select("20230001", "JAVA101").getStatus());

        assertEquals(1, service.selectedCourses("20230001").getData().size());
        assertEquals("JAVA101", service.selectedCourses("20230001").getData().get(0).getCourseId());
    }

    @Test
    void rejectsDuplicateSelectionAndFullCourse() {
        assertEquals(StatusCode.OK, service.select("20230001", "JAVA101").getStatus());

        assertEquals(StatusCode.CONFLICT, service.select("20230001", "JAVA101").getStatus());
        assertEquals(StatusCode.CONFLICT, service.select("20230002", "JAVA101").getStatus());
    }

    @Test
    void dropsExistingSelection() {
        service.select("20230001", "DB101");

        assertEquals(StatusCode.OK, service.drop("20230001", "DB101").getStatus());
        assertEquals(0, service.selectedCourses("20230001").getData().size());
        assertEquals(StatusCode.NOT_FOUND, service.drop("20230001", "DB101").getStatus());
    }

    private static void insertCourse(Connection connection, String courseId, String name,
            int credits, int capacity) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblCourse(course_id,course_name,credits,capacity) VALUES(?,?,?,?)")) {
            statement.setString(1, courseId);
            statement.setString(2, name);
            statement.setInt(3, credits);
            statement.setInt(4, capacity);
            statement.executeUpdate();
        }
    }
}
