package cn.vcampus.server;

import cn.vcampus.student.StudentRecord;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用临时 Access 数据库验证学生学籍记录的真实持久化行为。 */
class AccessStudentRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    private AccessStudentRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Path database = temporaryDirectory.resolve("student-test.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:ucanaccess://" + database
                        + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblStudent ("
                    + "student_id VARCHAR(32) NOT NULL, user_id VARCHAR(32),"
                    + "student_name VARCHAR(64) NOT NULL, gender VARCHAR(8),"
                    + "department_name VARCHAR(64), major_name VARCHAR(64),"
                    + "class_id VARCHAR(32), enrollment_year INTEGER, status VARCHAR(16) NOT NULL,"
                    + "phone VARCHAR(32), email VARCHAR(100), PRIMARY KEY (student_id))");
            insert(connection, new StudentRecord("S002", "u002", "李四", "男", "计算机学院",
                    "软件工程", "SE2023-01", 2023, "在读", "13900000002", "li@example.com"));
            insert(connection, new StudentRecord("S001", "u001", "张三", "女", "计算机学院",
                    "软件工程", "SE2023-01", 2023, "在读", "13900000001", "zhang@example.com"));
        }
        repository = new AccessStudentRepository(database);
    }

    @Test
    void findsByStudentIdAndUserId() {
        StudentRecord byStudentId = repository.findById("S001");
        assertNotNull(byStudentId);
        assertEquals("张三", byStudentId.getName());
        assertEquals("u001", byStudentId.getUserId());
        assertEquals("李四", repository.findByUserId("u002").getName());
        assertNull(repository.findById("missing"));
    }

    @Test
    void findsClassMembersInStableStudentIdOrder() {
        List<StudentRecord> records = repository.findByClass("SE2023-01");
        assertEquals(2, records.size());
        assertEquals("S001", records.get(0).getStudentId());
        assertEquals("S002", records.get(1).getStudentId());
    }

    @Test
    void findsMajorMembersInStableStudentIdOrder() {
        List<StudentRecord> records = repository.findByMajor("软件工程");
        assertEquals(2, records.size());
        assertEquals("S001", records.get(0).getStudentId());
        assertEquals("S002", records.get(1).getStudentId());
    }

    @Test
    void batchQueryPreservesRequestedOrderAndDeduplicatesIds() {
        List<StudentRecord> records = repository.findByIds(
                java.util.Arrays.asList("S002", "S001", "S002", "MISSING"));

        assertEquals(2, records.size());
        assertEquals("S002", records.get(0).getStudentId());
        assertEquals("S001", records.get(1).getStudentId());
        assertTrue(repository.findByIds(java.util.Collections.<String>emptyList()).isEmpty());
    }

    @Test
    void saveInsertsAndUpdatesAStudent() {
        StudentRecord created = new StudentRecord("S003", "u003", "王五", "男", "信息学院",
                "计算机科学", "CS2024-01", 2024, "在读", "13800000003", "wang@example.com");
        repository.save(created);
        assertEquals("王五", repository.findById("S003").getName());

        StudentRecord changed = new StudentRecord("S003", "u003", "王五", "男", "信息学院",
                "计算机科学", "CS2024-01", 2024, "休学", "13800000004", "new@example.com");
        repository.save(changed);
        assertEquals("休学", repository.findById("S003").getStatus());
        assertEquals("13800000004", repository.findById("S003").getPhone());
    }

    @Test
    void rejectsBindingOneUserToTwoStudents() {
        StudentRecord duplicate = new StudentRecord("S003", "u001", "王五", "男", "信息学院",
                "计算机科学", "CS2024-01", 2024, "在读", "", "");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> repository.save(duplicate));
        assertEquals("userId is already bound to another student", failure.getMessage());
    }

    @Test
    void rejectsBlankRequiredFields() {
        StudentRecord invalid = new StudentRecord("", "", "", "", "", "", 0, "", "", "");
        assertThrows(IllegalArgumentException.class, () -> repository.save(invalid));
        assertThrows(IllegalArgumentException.class, () -> repository.findByClass(" "));
    }

    private static void insert(Connection connection, StudentRecord record) throws Exception {
        try (java.sql.PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblStudent(student_id,user_id,student_name,gender,department_name,"
                        + "major_name,class_id,enrollment_year,status,phone,email) VALUES(?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, record.getStudentId());
            statement.setString(2, record.getUserId());
            statement.setString(3, record.getName());
            statement.setString(4, record.getGender());
            statement.setString(5, record.getDepartmentName());
            statement.setString(6, record.getMajorName());
            statement.setString(7, record.getClassId());
            statement.setInt(8, record.getEnrollmentYear());
            statement.setString(9, record.getStatus());
            statement.setString(10, record.getPhone());
            statement.setString(11, record.getEmail());
            statement.executeUpdate();
        }
    }
}
