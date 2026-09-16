package cn.vcampus.student;

import cn.vcampus.common.ServiceResult;
import java.util.List;
import java.math.BigDecimal;

/** Read-only academic history contract consumed by course selection. */
public interface AcademicReviewService {
    ServiceResult<List<CourseHistoryRecord>> historyFor(String studentId);
    ServiceResult<List<CourseHistoryRecord>> pendingRetakes(String studentId);
    ServiceResult<AcademicReview> review(String studentId, int requiredCredits);
    /** Precise-credit API. Legacy implementations reject fractional values instead of truncating. */
    default ServiceResult<AcademicReview> reviewDecimal(String studentId,
            BigDecimal requiredCredits) {
        if (requiredCredits == null) {
            return ServiceResult.failure(cn.vcampus.common.StatusCode.BAD_REQUEST,
                    "requiredCredits must not be null");
        }
        try {
            return review(studentId, requiredCredits.intValueExact());
        } catch (ArithmeticException fractionalOrOutOfRange) {
            return ServiceResult.failure(cn.vcampus.common.StatusCode.BAD_REQUEST,
                    "academic review implementation does not support fractional credits");
        }
    }
    ServiceResult<AcademicReview> latestReview(String studentId);
}
