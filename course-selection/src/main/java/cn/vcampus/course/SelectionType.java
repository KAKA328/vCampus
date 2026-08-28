package cn.vcampus.course;

/**
 * 学生在某个开课班次中的选课类别。
 *
 * <p>后续系统会根据学生的专业、年级和历史成绩，由服务端判断其类别，
 * 而不是相信客户端自行传入的类别。</p>
 */
public enum SelectionType {
    /** 按本专业培养计划正常修读必修课程。 */
    REQUIRED("必修", CapacityBucket.REQUIRED),
    /** 作为选修课程修读。 */
    ELECTIVE("选修", CapacityBucket.ELECTIVE),
    /** 跨专业修读允许开放的课程。 */
    CROSS_MAJOR("跨专业选修", CapacityBucket.CROSS_MAJOR),
    /** 因课程未通过而再次修读，与下一届必修生共同占用必修容量。 */
    RETAKE("重修", CapacityBucket.REQUIRED);

    private final String displayName;
    private final CapacityBucket capacityBucket;

    SelectionType(String displayName, CapacityBucket capacityBucket) {
        this.displayName = displayName;
        this.capacityBucket = capacityBucket;
    }

    /** 返回在客户端界面中使用的中文名称。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 返回该选课身份选中后需要占用的容量池。 */
    public CapacityBucket getCapacityBucket() {
        return capacityBucket;
    }
}
