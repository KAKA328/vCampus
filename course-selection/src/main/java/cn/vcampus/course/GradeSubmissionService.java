package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** 教学班成绩草稿的保存与读取接口。审核和正式成绩写入将在后续阶段补充。 */
public interface GradeSubmissionService {
    ServiceResult<GradeSubmission> createDraft(GradeSubmission submission);
    ServiceResult<GradeSubmission> findById(String submissionId);
    ServiceResult<GradeSubmission> findByOffering(String offeringId);
    ServiceResult<List<GradeSubmission>> listByStatus(GradeSubmissionStatus status);
    ServiceResult<List<GradeEntry>> listEntries(String submissionId);

    /** 允许教师在草稿、被退回或待审核状态下新增、覆盖同一学生的成绩。 */
    ServiceResult<GradeEntry> saveDraftEntry(GradeEntry entry);

    /** 将完整成绩单提交（或再次提交）为待教务审核状态。 */
    ServiceResult<GradeSubmission> submitForReview(String submissionId);

    /** 仅处理待审核成绩单；退回时必须保存教务老师的意见。 */
    ServiceResult<GradeSubmission> review(String submissionId, GradeReviewDecision decision,
            String reviewerId, String remark);
}
