package cn.vcampus.course;

/** 培养方案的生命周期状态。 */
public enum TrainingPlanStatus {
    /** 教务人员尚在维护，学生不可见。 */
    DRAFT,
    /** 已确认并对符合条件的学生生效。 */
    PUBLISHED,
    /** 保留历史记录，不再供学生查询。 */
    ARCHIVED
}
