package cn.vcampus.course;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学生在某个选课轮次中选择教学班后形成的正式记录。
 *
 * <p>记录保留选课类别，因此任课老师查询教学班名单时能够区分首修、选修和重修学生；
 * 退选时改为 {@link SelectionRecordStatus#DROPPED}，而不是删除历史记录。</p>
 */
public final class CourseSelectionRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String recordId;
    private final String studentId;
    private final String offeringId;
    private final String roundId;
    private final SelectionType selectionType;
    private final LocalDateTime selectedAt;
    private final SelectionRecordStatus status;
    private final LocalDateTime droppedAt;

    public CourseSelectionRecord(String recordId, String studentId, String offeringId, String roundId,
            SelectionType selectionType, LocalDateTime selectedAt) {
        this(recordId, studentId, offeringId, roundId, selectionType, selectedAt,
                SelectionRecordStatus.ACTIVE, null);
    }

    private CourseSelectionRecord(String recordId, String studentId, String offeringId, String roundId,
            SelectionType selectionType, LocalDateTime selectedAt, SelectionRecordStatus status,
            LocalDateTime droppedAt) {
        this.recordId = requireText(recordId, "recordId");
        this.studentId = requireText(studentId, "studentId");
        this.offeringId = requireText(offeringId, "offeringId");
        this.roundId = requireText(roundId, "roundId");
        if (selectionType == null) {
            throw new IllegalArgumentException("selectionType must not be null");
        }
        if (selectedAt == null) {
            throw new IllegalArgumentException("selectedAt must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (status == SelectionRecordStatus.ACTIVE && droppedAt != null) {
            throw new IllegalArgumentException("active selection must not have droppedAt");
        }
        if (status == SelectionRecordStatus.DROPPED) {
            if (droppedAt == null) {
                throw new IllegalArgumentException("dropped selection must have droppedAt");
            }
            if (droppedAt.isBefore(selectedAt)) {
                throw new IllegalArgumentException("droppedAt must not be before selectedAt");
            }
        }
        this.selectionType = selectionType;
        this.selectedAt = selectedAt;
        this.status = status;
        this.droppedAt = droppedAt;
    }

    public String getRecordId() {
        return recordId;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getOfferingId() {
        return offeringId;
    }

    public String getRoundId() {
        return roundId;
    }

    public SelectionType getSelectionType() {
        return selectionType;
    }

    public LocalDateTime getSelectedAt() {
        return selectedAt;
    }

    public SelectionRecordStatus getStatus() {
        return status;
    }

    public LocalDateTime getDroppedAt() {
        return droppedAt;
    }

    public boolean isActive() {
        return status == SelectionRecordStatus.ACTIVE;
    }

    /**
     * 返回退选后的新记录，原记录保持不变。
     */
    public CourseSelectionRecord withDroppedAt(LocalDateTime newDroppedAt) {
        if (!isActive()) {
            throw new IllegalStateException("selection record is already dropped");
        }
        return new CourseSelectionRecord(recordId, studentId, offeringId, roundId, selectionType,
                selectedAt, SelectionRecordStatus.DROPPED, newDroppedAt);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
