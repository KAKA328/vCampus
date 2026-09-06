package cn.vcampus.student;

import cn.vcampus.common.*;
import java.util.*;

public final class InMemoryAcademicAdminStore implements AcademicAdminStore {
    private final StudentManagementService students;
    private final TeacherProfileService teachers;
    private final AcademicReviewService academics;
    private final Map<String, AcademicAssessment> assessments = new LinkedHashMap<String, AcademicAssessment>();
    public InMemoryAcademicAdminStore(StudentManagementService students,
            TeacherProfileService teachers, AcademicReviewService academics) {
        this.students = students; this.teachers = teachers; this.academics = academics;
    }
    @Override public synchronized <T> T transaction(Work<T> work) throws Exception {
        // Share the same locks as in-memory history writes and service-level profile saves.
        synchronized (students) {
            synchronized (academics) {
                return work.run(new Context() {
            @Override public List<StudentRecord> students() { return require(students.findAll()); }
            @Override public List<TeacherProfile> teachers() { return require(teachers.findAll()); }
            @Override public StudentRecord student(String id) {
                ServiceResult<StudentRecord> result = students.findById(id);
                return result.getStatus() == StatusCode.NOT_FOUND ? null : require(result);
            }
            @Override public List<CourseHistoryRecord> history(String id) { return require(academics.historyFor(id)); }
            @Override public List<AcademicAssessment> assessments(String id) {
                List<AcademicAssessment> found = new ArrayList<AcademicAssessment>();
                for (AcademicAssessment row : assessments.values()) if (id.equals(row.getStudentId())) found.add(row);
                Collections.reverse(found);
                return found;
            }
            @Override public void save(AcademicAssessment row) { assessments.put(row.getId(), row); }
            @Override public void graduate(StudentRecord previous, AcademicAssessment assessment) {
                require(students.save(new StudentRecord(previous.getStudentId(), previous.getUserId(),
                        previous.getName(), previous.getGender(), previous.getDepartmentName(),
                        previous.getMajorName(), previous.getClassId(), previous.getEnrollmentYear(),
                        "毕业", previous.getPhone(), previous.getEmail())));
                assessments.put(assessment.getId(), assessment);
            }
                });
            }
        }
    }
    private static <T> T require(ServiceResult<T> result) {
        if (result.getStatus() != StatusCode.OK) throw new IllegalStateException(result.getMessage());
        return result.getData();
    }
}
