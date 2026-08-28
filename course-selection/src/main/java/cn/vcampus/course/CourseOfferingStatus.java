package cn.vcampus.course;

/**
 * 教学班在选课流程中的状态。
 *
 * <p>草稿状态仅供教务人员编辑；只有开放状态的教学班才能在后续选课服务中被学生选择。</p>
 */
public enum CourseOfferingStatus {
    /** 教务人员正在创建或编辑，学生不可见。 */
    DRAFT("草稿"),
    /** 已开放给符合条件的学生选课。 */
    OPEN("开放选课"),
    /** 选课结束，保留记录但不再接受新的选课请求。 */
    CLOSED("停止选课");

    private final String displayName;

    CourseOfferingStatus(String displayName) {
        this.displayName = displayName;
    }

    /** 返回在教务管理页面中使用的中文名称。 */
    public String getDisplayName() {
        return displayName;
    }
}
