package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.course.GradeSubmission;
import cn.vcampus.student.FormalCourseResult;
import java.util.List;

/** 将成绩单审核通过与正式成绩发布组合为一个不可拆分的流程。 */
interface GradeApprovalWorkflow {
    ServiceResult<GradeSubmission> approve(String submissionId, List<FormalCourseResult> results,
            String reviewerId, String remark);
}
