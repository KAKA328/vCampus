package cn.vcampus.student;

import java.io.Serializable;

/** Teacher archive used by course offering and grade-entry authorization. */
public final class TeacherProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String teacherId;
    private final String userId;
    private final String teacherName;
    private final String departmentName;
    private final String title;
    private final boolean active;

    public TeacherProfile(String teacherId, String userId, String teacherName,
            String departmentName, String title, boolean active) {
        this.teacherId = requireText(teacherId, "teacherId");
        this.userId = normalize(userId);
        this.teacherName = requireText(teacherName, "teacherName");
        this.departmentName = normalize(departmentName);
        this.title = normalize(title);
        this.active = active;
    }

    public String getTeacherId() { return teacherId; }
    public String getUserId() { return userId; }
    public String getTeacherName() { return teacherName; }
    public String getDepartmentName() { return departmentName; }
    public String getTitle() { return title; }
    public boolean isActive() { return active; }

    private static String requireText(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
