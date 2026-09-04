package cn.vcampus.student;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InMemoryAcademicReviewServiceTest {
    @Test
    public void reviewCountsRetakePassedCourseCreditsOnlyOnce() {
        InMemoryAcademicReviewService service = new InMemoryAcademicReviewService();
        service.addHistory(new CourseHistoryRecord("S001", "C001", "Java程序设计", "2025-2026-1", 1, "首修", 48, false, 0));
        service.addHistory(new CourseHistoryRecord("S001", "C001", "Java程序设计", "2025-2026-2", 2, "重修", 76, true, 3));
        service.addHistory(new CourseHistoryRecord("S001", "C002", "数据库原理", "2025-2026-2", 1, "首修", 86, true, 3));

        ServiceResult<AcademicReview> result = service.review("S001", 6);

        assertEquals(StatusCode.OK, result.getStatus());
        assertTrue(result.getData().isGraduationReady());
        assertEquals(6, result.getData().getTotalEarnedCredits());
        assertEquals(6, result.getData().getRequiredEarnedCredits());
        assertEquals(0, result.getData().getCreditShortfall());
        assertEquals(2, result.getData().getPassedCourseCount());
        assertEquals(0, result.getData().getFailedCourseCount());
        assertEquals(1, result.getData().getRetakeCourseCount());
        assertEquals(StatusCode.OK, service.latestReview("S001").getStatus());
        assertEquals(6, service.latestReview("S001").getData().getTotalEarnedCredits());
    }

    @Test
    public void reviewReportsMissingCreditsAndUnresolvedFailures() {
        InMemoryAcademicReviewService service = new InMemoryAcademicReviewService();
        service.addHistory(new CourseHistoryRecord("S002", "C001", "Java程序设计", "2025-2026-1", 1, "首修", 51, false, 0));
        service.addHistory(new CourseHistoryRecord("S002", "C002", "数据库原理", "2025-2026-1", 1, "首修", 80, true, 3));

        ServiceResult<AcademicReview> result = service.review("S002", 6);

        assertEquals(StatusCode.OK, result.getStatus());
        assertFalse(result.getData().isGraduationReady());
        assertEquals(3, result.getData().getTotalEarnedCredits());
        assertEquals(6, result.getData().getRequiredEarnedCredits());
        assertEquals(3, result.getData().getCreditShortfall());
        assertEquals(1, result.getData().getPassedCourseCount());
        assertEquals(1, result.getData().getFailedCourseCount());
        assertEquals(0, result.getData().getRetakeCourseCount());
    }

    @Test
    public void historyForUnknownStudentIsEmpty() {
        InMemoryAcademicReviewService service = new InMemoryAcademicReviewService();

        ServiceResult<List<CourseHistoryRecord>> result = service.historyFor("UNKNOWN");

        assertEquals(StatusCode.OK, result.getStatus());
        assertTrue(result.getData().isEmpty());
    }

    @Test
    public void pendingRetakesOnlyReturnsCoursesWithoutAnyPassingAttempt() {
        InMemoryAcademicReviewService service = new InMemoryAcademicReviewService();
        service.addHistory(new CourseHistoryRecord("S003", "C001", "Java程序设计", "2025-2026-1", 1, "首修", 48, false, 0));
        service.addHistory(new CourseHistoryRecord("S003", "C001", "Java程序设计", "2025-2026-2", 2, "重修", 76, true, 3));
        service.addHistory(new CourseHistoryRecord("S003", "C002", "数据库原理", "2025-2026-1", 1, "首修", 50, false, 0));
        service.addHistory(new CourseHistoryRecord("S003", "C002", "数据库原理", "2025-2026-2", 2, "重修", 55, false, 0));

        ServiceResult<List<CourseHistoryRecord>> result = service.pendingRetakes("S003");

        assertEquals(StatusCode.OK, result.getStatus());
        assertEquals(1, result.getData().size());
        assertEquals("C002", result.getData().get(0).getCourseId());
        assertEquals(2, result.getData().get(0).getAttemptNo());
    }

    @Test
    public void pendingRetakesRejectsBlankStudentId() {
        ServiceResult<List<CourseHistoryRecord>> result =
                new InMemoryAcademicReviewService().pendingRetakes(" ");

        assertEquals(StatusCode.BAD_REQUEST, result.getStatus());
    }

    @Test
    public void latestReviewReportsNotFoundBeforeAnyReview() {
        assertEquals(StatusCode.NOT_FOUND,
                new InMemoryAcademicReviewService().latestReview("S001").getStatus());
    }

    @Test
    public void historyForRejectsBlankStudentIdAndNormalizesLookup() {
        InMemoryAcademicReviewService service = new InMemoryAcademicReviewService();
        service.addHistory(new CourseHistoryRecord("S004", "C001", "课程", "2026-2027-1",
                1, "首修", 80, true, 3));

        assertEquals(StatusCode.BAD_REQUEST, service.historyFor(" ").getStatus());
        assertEquals(1, service.historyFor(" S004 ").getData().size());
    }

    @Test
    public void addHistoryRejectsNullRecord() {
        assertEquals(StatusCode.BAD_REQUEST,
                new InMemoryAcademicReviewService().addHistory(null).getStatus());
    }

    @Test
    public void historyRecordRejectsBlankIdentityFields() {
        assertInvalidHistory(null, "C001", "2026-2027-1", "首修");
        assertInvalidHistory("S001", " ", "2026-2027-1", "首修");
        assertInvalidHistory("S001", "C001", " ", "首修");
        assertInvalidHistory("S001", "C001", "2026-2027-1", null);
    }

    private static void assertInvalidHistory(String studentId, String courseId,
            String semester, String attemptType) {
        try {
            new CourseHistoryRecord(studentId, courseId, "课程", semester,
                    1, attemptType, 80, true, 3);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("must not be blank"));
        }
    }
}
