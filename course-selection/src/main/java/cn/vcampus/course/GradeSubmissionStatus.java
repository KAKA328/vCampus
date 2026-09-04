package cn.vcampus.course;

/** 一个教学班成绩单在教师提交和教务审核过程中的状态。 */
public enum GradeSubmissionStatus {
    /** 教师可持续录入、导入或修改成绩。 */
    DRAFT,
    /** 教师已提交，等待教务老师审核，教师不能继续修改。 */
    PENDING_REVIEW,
    /** 教务审核通过；下一阶段才会将其转换为正式课程结果。 */
    APPROVED,
    /** 教务退回成绩单，教师修改后可再次提交。 */
    RETURNED
}
