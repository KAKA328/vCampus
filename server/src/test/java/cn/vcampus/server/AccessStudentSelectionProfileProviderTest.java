package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.StatusCode;
import cn.vcampus.course.StudentSelectionProfile;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 验证 Access 学籍和成绩数据可被安全转换为选课资料。 */
class AccessStudentSelectionProfileProviderTest {
    @TempDir
    Path temporaryDirectory;

    private AccessStudentSelectionProfileProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        Path database = temporaryDirectory.resolve("student-selection-profile-test.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblStudent (student_id VARCHAR(32) NOT NULL,"
                    + "user_id VARCHAR(32),major_name VARCHAR(64) NOT NULL,"
                    + "enrollment_year INTEGER NOT NULL,status VARCHAR(16) NOT NULL,"
                    + "PRIMARY KEY (student_id))");
            statement.execute("CREATE TABLE tblSelectionRound (round_id VARCHAR(36) NOT NULL,"
                    + "term VARCHAR(32) NOT NULL,round_type VARCHAR(16) NOT NULL,"
                    + "starts_at DATETIME NOT NULL,ends_at DATETIME NOT NULL,"
                    + "status VARCHAR(16) NOT NULL,PRIMARY KEY (round_id))");
            statement.execute("CREATE TABLE tblCourseResult (result_id VARCHAR(36) NOT NULL,"
                    + "student_id VARCHAR(32) NOT NULL,course_id VARCHAR(32) NOT NULL,"
                    + "passed BIT NOT NULL,PRIMARY KEY (result_id))");
            statement.execute("INSERT INTO tblStudent(student_id,user_id,major_name,enrollment_year,status) "
                    + "VALUES ('S001','user-001','软件工程',2026,'在读')");
            statement.execute("INSERT INTO tblSelectionRound(round_id,term,round_type,starts_at,ends_at,status) "
                    + "VALUES ('ROUND-001','2026-2027-1','INITIAL',DATEADD('d',-1,NOW()),DATEADD('d',1,NOW()),'OPEN')");
            statement.execute("INSERT INTO tblCourseResult(result_id,student_id,course_id,passed) "
                    + "VALUES ('R001','S001','JAVA101',0)");
            statement.execute("INSERT INTO tblCourseResult(result_id,student_id,course_id,passed) "
                    + "VALUES ('R002','S001','JAVA101',1)");
            statement.execute("INSERT INTO tblCourseResult(result_id,student_id,course_id,passed) "
                    + "VALUES ('R003','S001','DB101',0)");
            statement.execute("INSERT INTO tblCourseResult(result_id,student_id,course_id,passed) "
                    + "VALUES ('R004','S001','DB101',0)");
        }
        provider = new AccessStudentSelectionProfileProvider(database);
    }

    @Test
    void createsProfileAndComputesPendingRetakesFromAccessData() {
        cn.vcampus.common.ServiceResult<StudentSelectionProfile> result =
                provider.findByUserId("user-001");

        assertEquals(StatusCode.OK, result.getStatus());
        assertEquals("S001", result.getData().getStudentId());
        assertEquals("2026-2027-1", result.getData().getCurrentTerm());
        assertEquals(1, result.getData().getRecommendedTerm());
        assertEquals(1, result.getData().getPendingRetakeCourseIds().size());
        assertTrue(result.getData().getPendingRetakeCourseIds().contains("DB101"));
    }

    @Test
    void rejectsUserWithoutBoundStudentProfile() {
        assertEquals(StatusCode.NOT_FOUND, provider.findByUserId("unknown-user").getStatus());
    }
}
