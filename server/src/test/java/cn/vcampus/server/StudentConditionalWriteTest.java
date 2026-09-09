package cn.vcampus.server;

import cn.vcampus.common.*;
import cn.vcampus.student.*;
import java.nio.file.Path;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StudentConditionalWriteTest {
    @TempDir Path temporaryDirectory;

    @Test void memoryConditionalWritesProtectSnapshotAndBinding() throws Exception { verify(false); }
    @Test void accessConditionalWritesProtectSnapshotAndBinding() throws Exception { verify(true); }

    private void verify(boolean access) throws Exception {
        StudentRepository repository;
        Path database = temporaryDirectory.resolve("conditional.accdb");
        if (access) {
            Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
            AccessDatabaseSchemaTest.executeScript(database, AccessDatabaseSchemaTest.readScript("database/schema.sql"));
            repository = new AccessStudentRepository(database);
        } else repository = new InMemoryStudentRepository();
        DefaultStudentManagementService service = new DefaultStudentManagementService(repository);
        StudentRecord original = row("S1", "u1", "在读", null);
        assertEquals(StatusCode.OK, service.saveIfUnchanged(original, null).getStatus());
        assertEquals(StatusCode.CONFLICT, service.saveIfUnchanged(row("S1", "u1", "毕业", null), null).getStatus());
        StudentRecord expected = repository.findById("S1");
        assertEquals(StatusCode.OK, service.saveIfUnchanged(StudentProfileSnapshot.withContacts(expected, "13800000001", null), expected).getStatus());
        assertEquals(StatusCode.CONFLICT, service.saveIfUnchanged(StudentProfileSnapshot.withContacts(expected, "13800000002", null), expected).getStatus());
        assertEquals("13800000001", repository.findById("S1").getPhone());
        expected = repository.findById("S1");
        assertEquals(StatusCode.FORBIDDEN, service.updateContacts("intruder", expected, "13800000003", null).getStatus());
        repository.save(row("S1", "new_owner", "在读", "13800000001"));
        assertEquals(StatusCode.CONFLICT, service.updateContacts("u1", expected, "13800000003", null).getStatus());
        assertEquals("new_owner", repository.findById("S1").getUserId());
        assertEquals("13800000001", repository.findById("S1").getPhone());
        // Null expected profile creates only; duplicate account binding remains a conflict.
        assertEquals(StatusCode.CONFLICT, service.saveIfUnchanged(row("S2", "new_owner", "在读", null), null).getStatus());
        assertNull(repository.findById("S2"));
        // SQL nullable fields and empty optional fields round-trip without spuriously failing CAS.
        repository.save(row("S3", null, "在读", ""));
        expected = repository.findById("S3");
        assertEquals(StatusCode.OK, service.saveIfUnchanged(row("S3", "u3", "在读", "13800000004"), expected).getStatus());
        if (access) {
            try (Connection c = DriverManager.getConnection("jdbc:ucanaccess://" + database
                    + ";immediatelyReleaseResources=true"); Statement statement = c.createStatement()) {
                statement.executeUpdate("UPDATE tblStudent SET enrollment_year=NULL WHERE student_id='S3'");
            }
            expected = repository.findById("S3");
            assertEquals(0, expected.getEnrollmentYear());
            assertEquals(StatusCode.OK, service.updateContacts("u3", expected, null, null).getStatus());
        }
    }
    private static StudentRecord row(String id, String user, String status, String phone) {
        return new StudentRecord(id, user, "学生", null, null, null, null, 2026, status, phone, null);
    }
}
