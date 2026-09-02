package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.StudentSelectionProfile;
import cn.vcampus.course.StudentSelectionProfileProvider;
import cn.vcampus.student.AcademicReviewService;
import cn.vcampus.student.CourseHistoryRecord;
import cn.vcampus.student.StudentManagementService;
import cn.vcampus.student.StudentRecord;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Builds the course-selection profile from authoritative student services. */
public final class StudentSelectionProfileAdapter implements StudentSelectionProfileProvider {
    private final StudentManagementService students;
    private final AcademicReviewService academicReviews;
    private final String currentTerm;

    public StudentSelectionProfileAdapter(StudentManagementService students,
            AcademicReviewService academicReviews, String currentTerm) {
        if (students == null || academicReviews == null) {
            throw new IllegalArgumentException("student profile dependencies must not be null");
        }
        this.students = students;
        this.academicReviews = academicReviews;
        this.currentTerm = requireText(currentTerm, "currentTerm");
    }

    @Override
    public ServiceResult<StudentSelectionProfile> findByUserId(String userId) {
        ServiceResult<StudentRecord> profile = students.findByUserId(userId);
        if (profile.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(profile.getStatus(), profile.getMessage());
        }

        StudentRecord student = profile.getData();
        ServiceResult<List<CourseHistoryRecord>> pending =
                academicReviews.pendingRetakes(student.getStudentId());
        if (pending.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(pending.getStatus(), pending.getMessage());
        }

        try {
            return ServiceResult.ok(new StudentSelectionProfile(
                    requireText(student.getUserId(), "userId"),
                    student.getStudentId(),
                    student.getMajorName(),
                    student.getEnrollmentYear(),
                    student.getStatus(),
                    currentTerm,
                    recommendedTerm(currentTerm, student.getEnrollmentYear()),
                    courseIds(pending.getData())));
        } catch (IllegalArgumentException invalidProfile) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR,
                    "student profile is incomplete for course selection");
        }
    }

    private static Set<String> courseIds(List<CourseHistoryRecord> records) {
        Set<String> courseIds = new LinkedHashSet<String>();
        for (CourseHistoryRecord record : records) {
            courseIds.add(record.getCourseId());
        }
        return courseIds;
    }

    /** Calculates a 1-based recommended semester from terms such as 2026-2027-1. */
    static int recommendedTerm(String currentTerm, int enrollmentYear) {
        String[] parts = requireText(currentTerm, "currentTerm").split("-");
        if (parts.length != 3) {
            throw new IllegalArgumentException("currentTerm must use YYYY-YYYY-S format");
        }
        try {
            int academicYear = Integer.parseInt(parts[0]);
            int semester = Integer.parseInt(parts[2]);
            if (semester < 1 || semester > 2 || academicYear < enrollmentYear) {
                throw new IllegalArgumentException("currentTerm is incompatible with enrollmentYear");
            }
            return (academicYear - enrollmentYear) * 2 + semester;
        } catch (NumberFormatException invalidTerm) {
            throw new IllegalArgumentException("currentTerm must use YYYY-YYYY-S format");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
