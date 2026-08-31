package cn.vcampus.course;

import java.io.Serializable;
import java.time.LocalDateTime;

/** A concrete V2 selection record created after a student selects an offering. */
public final class CourseSelectionRecord implements Serializable {
    private static final long serialVersionUID = 2L;

    private final String recordId;
    private final String studentId;
    private final String roundId;
    private final String offeringId;
    private final String courseId;
    private final SelectionType selectionType;
    private final LocalDateTime selectedAt;

    public CourseSelectionRecord(String recordId, String studentId, String roundId,
            String offeringId, String courseId, SelectionType selectionType, LocalDateTime selectedAt) {
        this.recordId = CourseProtocolText.requireText(recordId, "recordId");
        this.studentId = CourseProtocolText.requireText(studentId, "studentId");
        this.roundId = CourseProtocolText.requireText(roundId, "roundId");
        this.offeringId = CourseProtocolText.requireText(offeringId, "offeringId");
        this.courseId = CourseProtocolText.requireText(courseId, "courseId");
        if (selectionType == null) {
            throw new IllegalArgumentException("selectionType must not be null");
        }
        if (selectedAt == null) {
            throw new IllegalArgumentException("selectedAt must not be null");
        }
        this.selectionType = selectionType;
        this.selectedAt = selectedAt;
    }

    public String getRecordId() { return recordId; }
    public String getStudentId() { return studentId; }
    public String getRoundId() { return roundId; }
    public String getOfferingId() { return offeringId; }
    public String getCourseId() { return courseId; }
    public SelectionType getSelectionType() { return selectionType; }
    public LocalDateTime getSelectedAt() { return selectedAt; }
}
