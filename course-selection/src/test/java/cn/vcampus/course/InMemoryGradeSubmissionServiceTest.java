package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.StatusCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 验证成绩草稿支持反复修改同一学生的分数，但每个教学班只能有一份草稿。 */
class InMemoryGradeSubmissionServiceTest {
    @Test
    void savesAndReplacesOneStudentsDraftScore() {
        InMemoryGradeSubmissionService service = new InMemoryGradeSubmissionService();
        LocalDateTime now = LocalDateTime.of(2026, 9, 4, 10, 0);
        GradeSubmission submission = GradeSubmission.draft("GRADE-001", "OFFER-001", "T001", now);

        assertEquals(StatusCode.OK, service.createDraft(submission).getStatus());
        assertEquals(StatusCode.OK, service.saveDraftEntry(new GradeEntry("GRADE-001", "S001",
                SelectionType.REQUIRED, 72, now)).getStatus());
        assertEquals(StatusCode.OK, service.saveDraftEntry(new GradeEntry("GRADE-001", "S001",
                SelectionType.REQUIRED, 86, now.plusMinutes(1))).getStatus());

        assertEquals(1, service.listEntries("GRADE-001").getData().size());
        assertEquals(86, service.listEntries("GRADE-001").getData().get(0).getScore());
    }

    @Test
    void rejectsSecondSubmissionForSameOffering() {
        InMemoryGradeSubmissionService service = new InMemoryGradeSubmissionService();
        LocalDateTime now = LocalDateTime.of(2026, 9, 4, 10, 0);
        service.createDraft(GradeSubmission.draft("GRADE-001", "OFFER-001", "T001", now));

        assertEquals(StatusCode.CONFLICT, service.createDraft(
                GradeSubmission.draft("GRADE-002", "OFFER-001", "T001", now)).getStatus());
    }

    @Test
    void keepsLatestTeacherChangesAvailableForReviewAfterSubmission() {
        InMemoryGradeSubmissionService service = new InMemoryGradeSubmissionService();
        LocalDateTime now = LocalDateTime.of(2026, 9, 4, 10, 0);
        service.createDraft(GradeSubmission.draft("GRADE-001", "OFFER-001", "T001", now));
        service.saveDraftEntry(new GradeEntry("GRADE-001", "S001", SelectionType.REQUIRED,
                72, now));

        assertEquals(GradeSubmissionStatus.PENDING_REVIEW,
                service.submitForReview("GRADE-001").getData().getStatus());
        assertEquals(StatusCode.OK, service.saveDraftEntry(new GradeEntry("GRADE-001", "S001",
                SelectionType.REQUIRED, 86, now.plusMinutes(1))).getStatus());
        assertEquals(86, service.listEntries("GRADE-001").getData().get(0).getScore());
        assertEquals(GradeSubmissionStatus.PENDING_REVIEW,
                service.findById("GRADE-001").getData().getStatus());
    }

    @Test
    void reviewerCanReturnOrApproveOnlyPendingSubmission() {
        InMemoryGradeSubmissionService service = new InMemoryGradeSubmissionService();
        LocalDateTime now = LocalDateTime.of(2026, 9, 4, 10, 0);
        service.createDraft(GradeSubmission.draft("GRADE-001", "OFFER-001", "T001", now));
        service.submitForReview("GRADE-001");

        assertEquals(GradeSubmissionStatus.RETURNED, service.review("GRADE-001",
                GradeReviewDecision.RETURN, "academic_001", "请核对学号 S001 的成绩")
                .getData().getStatus());
        assertEquals("请核对学号 S001 的成绩", service.findById("GRADE-001").getData()
                .getReviewRemark());

        service.submitForReview("GRADE-001");
        assertEquals(GradeSubmissionStatus.APPROVED, service.review("GRADE-001",
                GradeReviewDecision.APPROVE, "academic_001", "审核通过").getData().getStatus());
        assertEquals(StatusCode.CONFLICT, service.review("GRADE-001", GradeReviewDecision.APPROVE,
                "academic_001", "重复审核").getStatus());
    }
}
