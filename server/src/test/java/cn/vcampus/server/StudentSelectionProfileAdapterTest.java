package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.StudentSelectionProfile;
import cn.vcampus.student.DefaultStudentManagementService;
import cn.vcampus.student.InMemoryAcademicReviewService;
import cn.vcampus.student.InMemoryStudentRepository;
import cn.vcampus.student.CourseHistoryRecord;
import cn.vcampus.student.StudentRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentSelectionProfileAdapterTest {
    @Test
    void buildsCourseProfileFromBoundStudentAndPendingRetakes() {
        InMemoryStudentRepository repository = new InMemoryStudentRepository();
        repository.save(student("STU-001", "login_001", "在读"));
        InMemoryAcademicReviewService academicReviews = new InMemoryAcademicReviewService();
        academicReviews.addHistory(history("STU-001", "JAVA101", 1, 48, false));
        academicReviews.addHistory(history("STU-001", "JAVA101", 2, 76, true));
        academicReviews.addHistory(history("STU-001", "DB101", 1, 52, false));
        StudentSelectionProfileAdapter adapter = new StudentSelectionProfileAdapter(
                new DefaultStudentManagementService(repository), academicReviews, "2026-2027-1");

        ServiceResult<StudentSelectionProfile> result = adapter.findByUserId("login_001");

        assertEquals(StatusCode.OK, result.getStatus());
        assertEquals("STU-001", result.getData().getStudentId());
        assertEquals("login_001", result.getData().getUserId());
        assertEquals(1, result.getData().getRecommendedTerm());
        assertEquals(1, result.getData().getPendingRetakeCourseIds().size());
        assertTrue(result.getData().getPendingRetakeCourseIds().contains("DB101"));
    }

    @Test
    void unboundAccountReturnsNotFound() {
        StudentSelectionProfileAdapter adapter = new StudentSelectionProfileAdapter(
                new DefaultStudentManagementService(new InMemoryStudentRepository()),
                new InMemoryAcademicReviewService(), "2026-2027-1");

        assertEquals(StatusCode.NOT_FOUND, adapter.findByUserId("unbound").getStatus());
    }

    @Test
    void calculatesRecommendedTermFromEnrollmentYear() {
        assertEquals(4, StudentSelectionProfileAdapter.recommendedTerm("2027-2028-2", 2026));
    }

    private static StudentRecord student(String studentId, String userId, String status) {
        return new StudentRecord(studentId, userId, "测试学生", "男", "计算机学院",
                "计算机科学与技术", "CS2026-01", 2026, status, "", "");
    }

    private static CourseHistoryRecord history(String studentId, String courseId,
            int attemptNo, int score, boolean passed) {
        return new CourseHistoryRecord(studentId, courseId, courseId,
                attemptNo == 1 ? "2025-2026-1" : "2025-2026-2",
                attemptNo, attemptNo == 1 ? "首修" : "重修", score, passed,
                passed ? 3 : 0);
    }
}
