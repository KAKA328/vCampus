package cn.vcampus.server;

import cn.vcampus.student.TeacherProfile;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessTeacherRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    private AccessTeacherRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Path database = temporaryDirectory.resolve("teacher-profile.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblTeacher (teacher_id VARCHAR(32) NOT NULL,"
                    + "user_id VARCHAR(32),teacher_name VARCHAR(64) NOT NULL,"
                    + "department_name VARCHAR(64),title VARCHAR(32),active BIT NOT NULL,"
                    + "PRIMARY KEY (teacher_id))");
            statement.execute("INSERT INTO tblTeacher(teacher_id,user_id,teacher_name,"
                    + "department_name,title,active) VALUES "
                    + "('T001','teacher001','张老师','计算机学院','讲师',1)");
        }
        repository = new AccessTeacherRepository(database);
    }

    @Test
    void findsTeacherAndMapsActiveStatus() {
        TeacherProfile profile = repository.findByUserId("teacher001");

        assertNotNull(profile);
        assertEquals("T001", profile.getTeacherId());
        assertTrue(profile.isActive());
    }

    @Test
    void insertsAndUpdatesTeacherProfile() {
        repository.save(new TeacherProfile("T002", null, "李老师", "数学学院", "教授", true));
        assertTrue(repository.findById("T002").isActive());

        repository.save(new TeacherProfile("T002", "teacher002", "李老师", "数学学院",
                "教授", false));
        assertFalse(repository.findById("T002").isActive());
        assertEquals("teacher002", repository.findById("T002").getUserId());
    }

    @Test
    void rejectsDuplicateAccountBinding() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> repository.save(new TeacherProfile("T002", "teacher001", "李老师",
                        "数学学院", "教授", true)));
        assertEquals("userId is already bound to another teacher", failure.getMessage());
    }
}
