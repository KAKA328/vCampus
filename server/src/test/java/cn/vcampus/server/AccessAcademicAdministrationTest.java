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
        service = service();
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
        assertEquals(8, ((List<?>) execute(Action.TEACHERS, 0, null).getData()).size());
        AcademicAssessment review = review();
        assertEquals(new java.math.BigDecimal("11"), review.getCredits().getEarnedCreditsDecimal());
        assertEquals(new java.math.BigDecimal("11"), review.getRequiredCreditsDecimal());
        assertEquals(StatusCode.OK, execute(Action.GRADUATE, 0, review.getId()).getStatus());
        assertEquals("毕业", new AccessStudentRepository(database).findById("20260001").getStatus());
        service = service();
        AcademicAssessment persisted = (AcademicAssessment) ((List<?>) execute(Action.ASSESSMENTS, 0, null).getData()).get(0);
        assertEquals("demo_academic_admin", persisted.getGraduatedBy());
        assertTrue(persisted.isGraduated());
        GraduationReviewOverview completed = service.overview("20260001").getData();
        assertTrue(completed.getLatestAssessment().isGraduated());
        assertTrue(completed.isLatestAssessmentCurrent());
        assertEquals("毕业条件已核查", persisted.getGraduationNote());
        assertEquals(StatusCode.CONFLICT, execute(Action.GRADUATE, 0, review.getId()).getStatus());
    }
    @Test void optionalNotesCanBeEmptyAndPersistOnExistingSchema() {
        for (String note : new String[] {null, "", "   "}) {
            ServiceResult<?> result = service.execute(new AcademicAdminCommandV2("token", Action.REVIEW,
                    "20260001", null, note, false), "demo_academic_admin");
            assertEquals(StatusCode.OK, result.getStatus(), result.getMessage());
            assertEquals("", ((AcademicAssessment) result.getData()).getBasis());
        }
        AcademicAssessment latest = (AcademicAssessment) ((List<?>) execute(Action.ASSESSMENTS, 0, null).getData()).get(0);
        ServiceResult<?> graduation = service.execute(new AcademicAdminCommandV2("token", Action.GRADUATE,
                "20260001", latest.getId(), null, true), "demo_academic_admin");
        assertEquals(StatusCode.OK, graduation.getStatus(), graduation.getMessage());
        service = service();
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
        assertEquals(new java.math.BigDecimal("11"), stored.getCredits().getEarnedCreditsDecimal());
        assertFalse(stored.isGraduated());
        assertEquals("在读", new AccessStudentRepository(database).findById("20260001").getStatus());
    }
    @Test void overviewReadsCurrentRequirementAndMarksPersistedSnapshotStale() throws Exception {
        GraduationReviewOverview initial = service.overview("20260001").getData();
        assertEquals(new java.math.BigDecimal("11"),
                initial.getCredits().getEarnedCreditsDecimal());
        assertEquals(new java.math.BigDecimal("11"),
                initial.getRequirement().getRequiredCreditsDecimal());
        assertEquals("plan-cs-2026", initial.getRequirement().getPlanId());
        assertNull(initial.getLatestAssessment());

        AcademicAssessment reviewed = review();
        service = service();
        GraduationReviewOverview current = service.overview("20260001").getData();
        assertEquals(reviewed.getId(), current.getLatestAssessment().getId());
        assertTrue(current.isLatestAssessmentCurrent());

        try (Connection c = open(); Statement q = c.createStatement()) {
            q.executeUpdate("UPDATE tblCourseResult SET score=87 WHERE result_id='result-java-demo-1'");
        }
        GraduationReviewOverview stale = service.overview("20260001").getData();
        assertEquals(reviewed.getId(), stale.getLatestAssessment().getId());
        assertFalse(stale.isLatestAssessmentCurrent());
    }
    @Test void courseCreditOrPlanStatusChangesInvalidateTheReview() throws Exception {
        AcademicAssessment creditReview = review();
        try (Connection c = open(); Statement q = c.createStatement()) {
            q.executeUpdate("UPDATE tblCourse SET credits=4 WHERE course_id='NET101'");
        }
        assertEquals(StatusCode.CONFLICT,
                execute(Action.GRADUATE, 0, creditReview.getId()).getStatus());

        try (Connection c = open(); Statement q = c.createStatement()) {
            q.executeUpdate("UPDATE tblCourse SET credits=3 WHERE course_id='NET101'");
        }
        AcademicAssessment statusReview = review();
        try (Connection c = open(); Statement q = c.createStatement()) {
            q.executeUpdate("UPDATE tblTrainingPlan SET status='DRAFT' WHERE plan_id='plan-cs-2026'");
        }
        assertEquals(StatusCode.NOT_FOUND,
                execute(Action.GRADUATE, 0, statusReview.getId()).getStatus());
    }
    @Test void failedGraduationRecordWriteRollsBackStudentStatus() throws Exception {
        AcademicAssessment review = review();
        AcademicAssessment missing = new AcademicAssessment("missing", review.getCredits(), 11, review.getEvidence(),
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
        return service.execute(new AcademicAdminCommandV2("internal-token", action, "20260001",
                id, "毕业条件已核查", true), "demo_academic_admin");
    }
    private AcademicAdminService service() {
        AccessCourseCatalogService catalog = new AccessCourseCatalogService(database);
        return new AcademicAdminService(new AccessAcademicAdminStore(database),
                new TrainingPlanGraduationCreditRequirementProvider(
                        new AccessTrainingPlanService(database, catalog), catalog));
    }
    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:ucanaccess://" + database + ";immediatelyReleaseResources=true");
    }
}
