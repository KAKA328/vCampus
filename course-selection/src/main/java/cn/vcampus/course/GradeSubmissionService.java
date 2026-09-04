package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** 教学班成绩草稿的保存与读取接口。审核和正式成绩写入将在后续阶段补充。 */
public interface GradeSubmissionService {
    ServiceResult<GradeSubmission> createDraft(GradeSubmission submission);
    ServiceResult<GradeSubmission> findById(String submissionId);
    ServiceResult<GradeSubmission> findByOffering(String offeringId);
    ServiceResult<List<GradeEntry>> listEntries(String submissionId);

    /** 仅允许在草稿或被退回状态下新增、覆盖同一学生的成绩。 */
    ServiceResult<GradeEntry> saveDraftEntry(GradeEntry entry);
}
