package cn.vcampus.course;

/**
 * 学生进入选课系统后可以参加的选课轮次类型。
 */
public enum SelectionRoundType {
    /** 学生按培养方案首次修读课程，可包含必修、选修和跨专业选修。 */
    INITIAL("首修轮次"),
    /** 学生针对历史未通过课程再次修读，只展示需要重修的课程。 */
    RETAKE("重修轮次");

    private final String displayName;

    SelectionRoundType(String displayName) {
        this.displayName = displayName;
    }

    /** 返回在学生选课页面中使用的中文名称。 */
    public String getDisplayName() {
        return displayName;
    }
}
