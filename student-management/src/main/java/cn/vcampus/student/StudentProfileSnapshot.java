package cn.vcampus.student;

import java.util.Objects;

/** Expected profile state for atomic writes; never trusts a client-supplied owner. */
public final class StudentProfileSnapshot {
    private StudentProfileSnapshot() { }

    public static boolean matches(StudentRecord current, StudentRecord expected) {
        if (current == null || expected == null) return current == expected;
        return Objects.equals(current.getStudentId(), expected.getStudentId())
                && Objects.equals(current.getUserId(), expected.getUserId())
                && Objects.equals(current.getName(), expected.getName())
                && Objects.equals(current.getGender(), expected.getGender())
                && Objects.equals(current.getDepartmentName(), expected.getDepartmentName())
                && Objects.equals(current.getMajorName(), expected.getMajorName())
                && Objects.equals(current.getClassId(), expected.getClassId())
                && current.getEnrollmentYear() == expected.getEnrollmentYear()
                && Objects.equals(current.getStatus(), expected.getStatus())
                && Objects.equals(current.getPhone(), expected.getPhone())
                && Objects.equals(current.getEmail(), expected.getEmail());
    }

    public static StudentRecord withContacts(StudentRecord profile, String phone, String email) {
        return new StudentRecord(profile.getStudentId(), profile.getUserId(), profile.getName(),
                profile.getGender(), profile.getDepartmentName(), profile.getMajorName(),
                profile.getClassId(), profile.getEnrollmentYear(), profile.getStatus(), phone, email);
    }
}
