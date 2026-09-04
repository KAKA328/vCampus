package cn.vcampus.server;

import cn.vcampus.common.StatusCode;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that teachers can only read students in their active offerings. */
class AccessTeacherStudentAccessPolicyTest {
    @TempDir
    Path temporaryDirectory;

    private AccessTeacherStudentAccessPolicy policy;

    @BeforeEach
    void setUp() throws Exception {
        Path database = temporaryDirectory.resolve("teacher-student-scope.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblTeacher (teacher_id VARCHAR(32) NOT NULL,"
                    + "user_id VARCHAR(32),PRIMARY KEY (teacher_id))");
            statement.execute("CREATE TABLE tblCourseOffering (offering_id VARCHAR(36) NOT NULL,"
                    + "teacher_id VARCHAR(32) NOT NULL,PRIMARY KEY (offering_id))");
            statement.execute("CREATE TABLE tblCourseSelection (selection_id VARCHAR(36) NOT NULL,"
                    + "student_id VARCHAR(32) NOT NULL,offering_id VARCHAR(36) NOT NULL,"
                    + "status VARCHAR(16) NOT NULL,PRIMARY KEY (selection_id))");
            statement.execute("INSERT INTO tblTeacher(teacher_id,user_id) VALUES ('T001','teacher001')");
            statement.execute("INSERT INTO tblCourseOffering(offering_id,teacher_id)"
                    + " VALUES ('O001','T001')");
            statement.execute("INSERT INTO tblCourseSelection(selection_id,student_id,offering_id,status)"
                    + " VALUES ('R001','S001','O001','ACTIVE')");
            statement.execute("INSERT INTO tblCourseSelection(selection_id,student_id,offering_id,status)"
                    + " VALUES ('R002','S002','O001','DROPPED')");
        }
        policy = new AccessTeacherStudentAccessPolicy(database);
    }

    @Test
    void allowsOnlyStudentsWithActiveSelectionInAssignedOffering() {
        assertTrue(policy.canRead("teacher001", "S001").getData());
        assertFalse(policy.canRead("teacher001", "S002").getData());
        assertFalse(policy.canRead("other-teacher", "S001").getData());
    }

    @Test
    void rejectsBlankIdentity() {
        assertEquals(StatusCode.BAD_REQUEST, policy.canRead(" ", "S001").getStatus());
        assertEquals(StatusCode.BAD_REQUEST, policy.canRead("teacher001", null).getStatus());
    }
}
