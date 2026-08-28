package cn.vcampus.course;

/**
 * 教务人员维护的选课轮次状态。
 */
public enum SelectionRoundStatus {
    /** 轮次正在配置，学生不可见。 */
    DRAFT("草稿"),
    /** 在规定时间内向符合条件的学生开放。 */
    OPEN("开放"),
    /** 轮次已结束，不再接受新的选课请求。 */
    CLOSED("关闭");

    private final String displayName;

    SelectionRoundStatus(String displayName) {
        this.displayName = displayName;
    }

    /** 返回在教务管理页面中使用的中文名称。 */
    public String getDisplayName() {
        return displayName;
    }
}
