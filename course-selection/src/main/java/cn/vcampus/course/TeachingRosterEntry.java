package cn.vcampus.course;

import java.io.Serializable;

/** 教师教学班名单中的一名有效选课学生。 */
public final class TeachingRosterEntry implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String studentId;
    private final String studentName;
    private final String majorName;
    private final String classId;
    private final SelectionType selectionType;

    public TeachingRosterEntry(String studentId, String studentName, String majorName, String classId,
            SelectionType selectionType) {
        this.studentId = requireText(studentId, "studentId");
        this.studentName = requireText(studentName, "studentName");
        this.majorName = normalize(majorName);
        this.classId = normalize(classId);
        if (selectionType == null) throw new IllegalArgumentException("selectionType must not be null");
        this.selectionType = selectionType;
    }

    public String getStudentId() { return studentId; }
    public String getStudentName() { return studentName; }
    public String getMajorName() { return majorName; }
    public String getClassId() { return classId; }
    public SelectionType getSelectionType() { return selectionType; }

    private static String requireText(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
