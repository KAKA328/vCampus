package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.GradeReviewDecision;
import cn.vcampus.course.GradeSubmission;
import cn.vcampus.course.GradeSubmissionService;
import cn.vcampus.course.GradeSubmissionStatus;
import cn.vcampus.student.CourseResultRecordingService;
import cn.vcampus.student.FormalCourseResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 内存演示模式的串行审核流程；Access 部署使用数据库事务实现。 */
final class InMemoryGradeApprovalWorkflow implements GradeApprovalWorkflow {
    private final GradeSubmissionService submissions;
    private final CourseResultRecordingService formalResults;
    private final Map<String, List<FormalCourseResult>> publishedResultsBySubmission =
            new LinkedHashMap<String, List<FormalCourseResult>>();

    InMemoryGradeApprovalWorkflow(GradeSubmissionService submissions,
            CourseResultRecordingService formalResults) {
        this.submissions = submissions;
        this.formalResults = formalResults;
    }

    @Override
    public synchronized ServiceResult<GradeSubmission> approve(String submissionId, int reviewVersionNo,
            List<FormalCourseResult> results, String reviewerId, String remark) {
        ServiceResult<GradeSubmission> current = submissions.findById(submissionId);
        if (current.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(current.getStatus(), current.getMessage());
        }
        if (current.getData().getStatus() != GradeSubmissionStatus.PENDING_REVIEW) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "only pending grade submissions can be reviewed");
        }
        ServiceResult<cn.vcampus.course.GradeReviewSnapshot> snapshot =
                submissions.findLatestReviewSnapshot(submissionId);
        if (snapshot.getStatus() != StatusCode.OK || snapshot.getData().getVersionNo() != reviewVersionNo) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "grade review snapshot was replaced by a newer submission");
        }
        ServiceResult<Void> saved = formalResults.recordAll(results);
        if (saved.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(saved.getStatus(), saved.getMessage());
        }
        ServiceResult<GradeSubmission> reviewed = submissions.review(submissionId,
                GradeReviewDecision.APPROVE, reviewerId, remark);
        if (reviewed.getStatus() != StatusCode.OK) {
            formalResults.retractAll(results);
            return reviewed;
        }
        publishedResultsBySubmission.put(submissionId,
                new ArrayList<FormalCourseResult>(results));
        return reviewed;
    }

    @Override
    public synchronized ServiceResult<GradeSubmission> returnForRevision(String submissionId,
            int reviewVersionNo,
            String reviewerId, String remark) {
        ServiceResult<GradeSubmission> current = submissions.findById(submissionId);
        if (current.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(current.getStatus(), current.getMessage());
        }
        if (current.getData().getStatus() == GradeSubmissionStatus.PENDING_REVIEW) {
            ServiceResult<cn.vcampus.course.GradeReviewSnapshot> snapshot =
                    submissions.findLatestReviewSnapshot(submissionId);
            if (snapshot.getStatus() != StatusCode.OK
                    || snapshot.getData().getVersionNo() != reviewVersionNo) {
                return ServiceResult.failure(StatusCode.CONFLICT,
                        "grade review snapshot was replaced by a newer submission");
            }
            return submissions.review(submissionId, GradeReviewDecision.RETURN, reviewerId, remark);
        }
        if (current.getData().getStatus() != GradeSubmissionStatus.APPROVED) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "only pending or approved grade submissions can be returned");
        }
        List<FormalCourseResult> published = publishedResultsBySubmission.get(submissionId);
        if (published == null || published.isEmpty()) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "approved grade submission has no published formal results");
        }
        ServiceResult<Void> retracted = formalResults.retractAll(published);
        if (retracted.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(retracted.getStatus(), retracted.getMessage());
        }
        ServiceResult<GradeSubmission> returned = submissions.review(submissionId,
                GradeReviewDecision.RETURN, reviewerId, remark);
        if (returned.getStatus() != StatusCode.OK) {
            formalResults.recordAll(published);
            return returned;
        }
        publishedResultsBySubmission.remove(submissionId);
        return returned;
    }
}
