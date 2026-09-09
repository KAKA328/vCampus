package cn.vcampus.server;

import cn.vcampus.common.*;
import cn.vcampus.student.*;
import cn.vcampus.student.AcademicAdminCommandV1.Action;
import java.nio.file.Path;
import java.sql.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AccessAcademicAdministrationTest {
    @TempDir Path directory;
    private Path database;
    private AcademicAdminService service;
    @BeforeEach void setup() throws Exception {
        database = directory.resolve("academic-admin.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        AccessDatabaseSchemaTest.executeScript(database, AccessDatabaseSchemaTest.readScript("database/schema.sql"));
        AccessDatabaseSchemaTest.executeScript(database, AccessDatabaseSchemaTest.readScript("database/seed.sql"));
        service = new AcademicAdminService(new AccessAcademicAdminStore(database));
    }
    @Test void directoryAssessmentGraduationAndRestartUseOneDatabase() {
        List<?> students = (List<?>) execute(Action.STUDENTS, 0, null).getData();
        assertTrue(students.size() >= 5);
        assertTrue(students.stream().anyMatch(item ->
                item instanceof StudentRecord
                        && "20260001".equals(((StudentRecord) item).getStudentId())));
        assertTrue(students.stream().anyMatch(item ->
                item instanceof StudentRecord
                        && "20260006".equals(((StudentRecord) item).getStudentId())));
        assertEquals(3, ((List<?>) execute(Action.TEACHERS, 0, null).getData()).size());
        AcademicAssessment review = review();
        assertEquals(6, review.getCredits().getEarnedCredits());
        assertEquals(StatusCode.OK, execute(Action.GRADUATE, 0, review.getId()).getStatus());
        assertEquals("毕业", new AccessStudentRepository(database).findById("20260001").getStatus());
        service = new AcademicAdminService(new AccessAcademicAdminStore(database));
        AcademicAssessment persisted = (AcademicAssessment) ((List<?>) execute(Action.ASSESSMENTS, 0, null).getData()).get(0);
        assertEquals("demo_academic_admin", persisted.getGraduatedBy());
        assertTrue(persisted.isGraduated());
        assertEquals("毕业条件已核查", persisted.getGraduationNote());
        assertEquals(StatusCode.CONFLICT, execute(Action.GRADUATE, 0, review.getId()).getStatus());
    }
    @Test void optionalNotesCanBeEmptyAndPersistOnExistingSchema() {
        for (String note : new String[] {null, "", "   "}) {
            ServiceResult<?> result = service.execute(new AcademicAdminCommandV1("token", Action.REVIEW,
                    "20260001", 6, null, note, false), "demo_academic_admin");
            assertEquals(StatusCode.OK, result.getStatus(), result.getMessage());
            assertEquals("", ((AcademicAssessment) result.getData()).getBasis());
        }
        AcademicAssessment latest = (AcademicAssessment) ((List<?>) execute(Action.ASSESSMENTS, 0, null).getData()).get(0);
        ServiceResult<?> graduation = service.execute(new AcademicAdminCommandV1("token", Action.GRADUATE,
                "20260001", 0, latest.getId(), null, true), "demo_academic_admin");
        assertEquals(StatusCode.OK, graduation.getStatus(), graduation.getMessage());
        service = new AcademicAdminService(new AccessAcademicAdminStore(database));
        AcademicAssessment saved = (AcademicAssessment) ((List<?>) execute(Action.ASSESSMENTS, 0, null).getData()).get(0);
        assertEquals("", saved.getBasis());
        assertEquals("", saved.getGraduationNote());
        assertTrue(saved.isGraduated());
    }
    @Test void changedScoreAndUpdatedReviewInvalidateOldEvidenceWithoutChangingStatus() throws Exception {
        AcademicAssessment initial = review();
        AcademicAssessment newer = review();
        assertEquals(StatusCode.CONFLICT, execute(Action.GRADUATE, 0, initial.getId()).getStatus());
        try (Connection c = open(); Statement q = c.createStatement()) {
            q.executeUpdate("UPDATE tblCourseResult SET score=87 WHERE result_id='result-java-demo-1'");
        }
        assertEquals(StatusCode.CONFLICT, execute(Action.GRADUATE, 0, newer.getId()).getStatus());
        AcademicAssessment stored = (AcademicAssessment) ((List<?>) execute(Action.ASSESSMENTS, 0, null).getData()).get(0);
        assertEquals(6, stored.getCredits().getEarnedCredits());
        assertFalse(stored.isGraduated());
        assertEquals("在读", new AccessStudentRepository(database).findById("20260001").getStatus());
    }
    @Test void failedGraduationRecordWriteRollsBackStudentStatus() throws Exception {
        AcademicAssessment review = review();
        AcademicAssessment missing = new AcademicAssessment("missing", review.getCredits(), 6, review.getEvidence(),
                review.getReviewedBy(), review.getReviewedAt(), "basis", null, null, null).graduate("actor", "note");
        AccessAcademicAdminStore store = new AccessAcademicAdminStore(database);
        assertThrows(SQLException.class, () -> store.transaction(context -> {
            context.graduate(context.student("20260001"), missing); return null;
        }));
        assertEquals("在读", new AccessStudentRepository(database).findById("20260001").getStatus());
        assertFalse(((AcademicAssessment) ((List<?>) execute(Action.ASSESSMENTS, 0, null).getData()).get(0)).isGraduated());
    }
    private AcademicAssessment review() {
        ServiceResult<?> result = execute(Action.REVIEW, 6, null);
        assertEquals(StatusCode.OK, result.getStatus(), result.getMessage());
        return (AcademicAssessment) result.getData();
    }
    private ServiceResult<?> execute(Action action, int credits, String id) {
        return service.execute(new AcademicAdminCommandV1("internal-token", action, "20260001",
                credits, id, "毕业条件已核查", true), "demo_academic_admin");
    }
    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:ucanaccess://" + database + ";immediatelyReleaseResources=true");
    }
}
