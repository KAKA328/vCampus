package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** 教务人员维护培养方案、学生端查询课程要求的业务接口。 */
public interface TrainingPlanService {
    ServiceResult<TrainingPlan> create(TrainingPlan plan);

    ServiceResult<TrainingPlan> findById(String planId);

    /** 供教务人员查看当前已维护的全部培养方案。 */
    ServiceResult<List<TrainingPlan>> listAll();

    /** 查询一个专业、一个入学年份适用的完整培养方案。 */
    ServiceResult<TrainingPlan> findByMajorAndEnrollmentYear(String majorName, int enrollmentYear);

    /** 发布或归档培养方案；状态只能按 DRAFT → PUBLISHED → ARCHIVED 前进。 */
    ServiceResult<TrainingPlan> changeStatus(String planId, TrainingPlanStatus status);

    /**
     * 在方案中新增课程，或按相同课程编号更新现有课程要求。
     */
    ServiceResult<TrainingPlan> saveCourse(String planId, TrainingPlanCourse course);

    /** 删除方案中的一门课程要求；不允许将培养方案删除为空。 */
    ServiceResult<TrainingPlan> removeCourse(String planId, String courseId);

    /** 查询学生当前第几个学期应看到的首修课程要求。 */
    ServiceResult<List<TrainingPlanCourse>> listCoursesByRecommendedTerm(String majorName,
            int enrollmentYear, int recommendedTerm);
}
