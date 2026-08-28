package cn.vcampus.course;

/**
 * 教学班中彼此独立计算的容量池。
 *
 * <p>容量池决定选课时扣减哪一类名额；它与学生的选课身份不是完全一一对应的。
 * 例如重修生保留 {@link SelectionType#RETAKE} 身份，但与下一届必修生共用必修容量。</p>
 */
public enum CapacityBucket {
    /** 面向正常必修和重修学生的共同名额。 */
    REQUIRED("必修容量"),
    /** 面向本专业选修学生的名额。 */
    ELECTIVE("选修容量"),
    /** 面向跨专业选修学生的名额。 */
    CROSS_MAJOR("跨专业容量");

    private final String displayName;

    CapacityBucket(String displayName) {
        this.displayName = displayName;
    }

    /** 返回在教务管理页面中使用的中文名称。 */
    public String getDisplayName() {
        return displayName;
    }
}
