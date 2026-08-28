package cn.vcampus.course;

/**
 * 学生在某个开课班次中的选课类别。
 *
 * <p>后续系统会根据学生的专业、年级和历史成绩，由服务端判断其类别，
 * 而不是相信客户端自行传入的类别。</p>
 */
public enum SelectionType {
    /** 按本专业培养计划正常修读。 */
    MAJOR("主修"),
    /** 作为选修课程修读。 */
    ELECTIVE("选修"),
    /** 因课程未通过而再次修读。 */
    RETAKE("重修");

    private final String displayName;

    SelectionType(String displayName) {
        this.displayName = displayName;
    }

    /** 返回在客户端界面中使用的中文名称。 */
    public String getDisplayName() {
        return displayName;
    }
}
