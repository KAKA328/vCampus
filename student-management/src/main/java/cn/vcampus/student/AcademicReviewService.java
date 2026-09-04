package cn.vcampus.student;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** Read-only academic history contract consumed by course selection. */
public interface AcademicReviewService {
    ServiceResult<List<CourseHistoryRecord>> historyFor(String studentId);
    ServiceResult<List<CourseHistoryRecord>> pendingRetakes(String studentId);
    ServiceResult<AcademicReview> review(String studentId, int requiredCredits);
    ServiceResult<AcademicReview> latestReview(String studentId);
}
