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
        assertEquals(2, result.getData().getPassedCourseCount());
        assertEquals(0, result.getData().getFailedCourseCount());
        assertEquals(1, result.getData().getRetakeCourseCount());
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
}
