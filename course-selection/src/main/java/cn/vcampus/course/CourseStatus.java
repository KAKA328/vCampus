package cn.vcampus.course;

/** 课程目录中课程的可用状态。 */
public enum CourseStatus {
    /** 可加入培养方案，也可据此开设新的教学班。 */
    ACTIVE("启用"),
    /** 保留历史信息，但不能加入新方案或新开教学班。 */
    DISABLED("停用");

    private final String displayName;

    CourseStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
