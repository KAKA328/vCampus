package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.StatusCode;
import cn.vcampus.course.GradeEntry;
import cn.vcampus.course.GradeSubmission;
import cn.vcampus.course.GradeSubmissionStatus;
import cn.vcampus.course.GradeReviewDecision;
import cn.vcampus.course.SelectionType;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 验证成绩草稿可真实保存到全新 Access 数据库，而不会触碰正式课程结果表。 */
class AccessGradeSubmissionServiceTest {
    @TempDir
    Path temporaryDirectory;

    private Path database;
    private AccessGradeSubmissionService service;

    @BeforeEach
    void setUp() throws Exception {
        database = temporaryDirectory.resolve("grade-submission.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblGradeSubmission ("
                    + "submission_id VARCHAR(36) NOT NULL,offering_id VARCHAR(36) NOT NULL,"
                    + "teacher_id VARCHAR(32) NOT NULL,status VARCHAR(20) NOT NULL,"
                    + "created_at DATETIME NOT NULL,updated_at DATETIME NOT NULL,"
                    + "reviewed_by VARCHAR(32),reviewed_at DATETIME,review_remark VARCHAR(255),"
                    + "PRIMARY KEY (submission_id),"
                    + "CONSTRAINT uk_tblGradeSubmission_offering UNIQUE (offering_id))");
            statement.execute("CREATE TABLE tblGradeEntry ("
                    + "submission_id VARCHAR(36) NOT NULL,student_id VARCHAR(32) NOT NULL,"
                    + "selection_type VARCHAR(16) NOT NULL,score INTEGER NOT NULL,updated_at DATETIME NOT NULL,"
                    + "PRIMARY KEY (submission_id,student_id))");
        }
        service = new AccessGradeSubmissionService(database);
    }

    @Test
    void persistsDraftAndOverwritesOneStudentsScore() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 4, 11, 0);
        assertEquals(StatusCode.OK, service.createDraft(
                GradeSubmission.draft("GRADE-001", "OFFER-001", "T001", now)).getStatus());
        assertEquals(StatusCode.OK, service.saveDraftEntry(new GradeEntry("GRADE-001", "S001",
                SelectionType.RETAKE, 61, now)).getStatus());
        assertEquals(StatusCode.OK, service.saveDraftEntry(new GradeEntry("GRADE-001", "S001",
                SelectionType.RETAKE, 74, now.plusMinutes(1))).getStatus());

        AccessGradeSubmissionService restarted = new AccessGradeSubmissionService(database);
        assertEquals("T001", restarted.findByOffering("OFFER-001").getData().getTeacherId());
        assertEquals(1, restarted.listEntries("GRADE-001").getData().size());
        assertEquals(74, restarted.listEntries("GRADE-001").getData().get(0).getScore());
    }

    @Test
    void rejectsSecondGradeSubmissionForSameOffering() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 4, 11, 0);
        service.createDraft(GradeSubmission.draft("GRADE-001", "OFFER-001", "T001", now));

        assertEquals(StatusCode.CONFLICT, service.createDraft(
                GradeSubmission.draft("GRADE-002", "OFFER-001", "T001", now)).getStatus());
    }

    @Test
    void persistsLatestTeacherChangeAfterSubmissionForReview() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 4, 11, 0);
        service.createDraft(GradeSubmission.draft("GRADE-001", "OFFER-001", "T001", now));
        service.saveDraftEntry(new GradeEntry("GRADE-001", "S001", SelectionType.REQUIRED,
                61, now));

        assertEquals(GradeSubmissionStatus.PENDING_REVIEW,
                service.submitForReview("GRADE-001").getData().getStatus());
        assertEquals(StatusCode.OK, service.saveDraftEntry(new GradeEntry("GRADE-001", "S001",
                SelectionType.REQUIRED, 74, now.plusMinutes(1))).getStatus());

        AccessGradeSubmissionService restarted = new AccessGradeSubmissionService(database);
        assertEquals(GradeSubmissionStatus.PENDING_REVIEW,
                restarted.findById("GRADE-001").getData().getStatus());
        assertEquals(74, restarted.listEntries("GRADE-001").getData().get(0).getScore());
    }

    @Test
    void persistsAcademicReturnRemarkAndLaterApproval() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 4, 11, 0);
        service.createDraft(GradeSubmission.draft("GRADE-001", "OFFER-001", "T001", now));
        service.submitForReview("GRADE-001");

        assertEquals(GradeSubmissionStatus.RETURNED, service.review("GRADE-001",
                GradeReviewDecision.RETURN, "academic_001", "请核对成绩").getData().getStatus());
        AccessGradeSubmissionService restarted = new AccessGradeSubmissionService(database);
        assertEquals("academic_001", restarted.findById("GRADE-001").getData().getReviewedBy());
        assertEquals("请核对成绩", restarted.findById("GRADE-001").getData().getReviewRemark());

        restarted.submitForReview("GRADE-001");
        assertEquals(GradeSubmissionStatus.APPROVED, restarted.review("GRADE-001",
                GradeReviewDecision.APPROVE, "academic_001", "审核通过").getData().getStatus());
    }

    @Test
    void rejectsInvalidBatchWithoutWritingAnyPartialAccessGrade() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 4, 11, 0);
        service.createDraft(GradeSubmission.draft("GRADE-001", "OFFER-001", "T001", now));

        assertEquals(StatusCode.BAD_REQUEST, service.saveDraftEntries(Arrays.asList(
                new GradeEntry("GRADE-001", "S001", SelectionType.REQUIRED, 60, now),
                new GradeEntry("GRADE-001", "S001", SelectionType.REQUIRED, 80, now)))
                .getStatus());
        assertEquals(0, service.listEntries("GRADE-001").getData().size());
    }
}
