package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.GradeReviewDecision;
import cn.vcampus.course.GradeSubmission;
import cn.vcampus.course.GradeSubmissionService;
import cn.vcampus.course.GradeSubmissionStatus;
import cn.vcampus.student.CourseResultRecordingService;
import cn.vcampus.student.FormalCourseResult;
import java.util.List;

/** 内存演示模式的串行审核流程；Access 部署使用数据库事务实现。 */
final class InMemoryGradeApprovalWorkflow implements GradeApprovalWorkflow {
    private final GradeSubmissionService submissions;
    private final CourseResultRecordingService formalResults;

    InMemoryGradeApprovalWorkflow(GradeSubmissionService submissions,
            CourseResultRecordingService formalResults) {
        this.submissions = submissions;
        this.formalResults = formalResults;
    }

    @Override
    public synchronized ServiceResult<GradeSubmission> approve(String submissionId,
            List<FormalCourseResult> results, String reviewerId, String remark) {
        ServiceResult<GradeSubmission> current = submissions.findById(submissionId);
        if (current.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(current.getStatus(), current.getMessage());
        }
        if (current.getData().getStatus() != GradeSubmissionStatus.PENDING_REVIEW) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "only pending grade submissions can be reviewed");
        }
        ServiceResult<Void> saved = formalResults.recordAll(results);
        if (saved.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(saved.getStatus(), saved.getMessage());
        }
        return submissions.review(submissionId, GradeReviewDecision.APPROVE, reviewerId, remark);
    }
}
