package cn.vcampus.course;

/**
 * 一条选课记录的当前状态。
 */
public enum SelectionRecordStatus {
    /** 学生当前仍在该教学班名单中。 */
    ACTIVE("已选"),
    /** 学生已退选，记录保留用于审计但不计入当前名单和容量。 */
    DROPPED("已退选");

    private final String displayName;

    SelectionRecordStatus(String displayName) {
        this.displayName = displayName;
    }

    /** 返回在客户端和管理页面中使用的中文名称。 */
    public String getDisplayName() {
        return displayName;
    }
}
