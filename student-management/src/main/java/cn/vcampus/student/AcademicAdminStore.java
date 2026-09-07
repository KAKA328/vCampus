package cn.vcampus.student;

import java.util.List;

/** One unit of work must atomically persist graduation evidence and student status. */
public interface AcademicAdminStore {
    interface Work<T> { T run(Context context) throws Exception; }
    interface Context {
        List<StudentRecord> students() throws Exception;
        List<TeacherProfile> teachers() throws Exception;
        StudentRecord student(String id) throws Exception;
        List<CourseHistoryRecord> history(String id) throws Exception;
        List<AcademicAssessment> assessments(String id) throws Exception;
        void save(AcademicAssessment assessment) throws Exception;
        void graduate(StudentRecord previous, AcademicAssessment assessment) throws Exception;
    }
    <T> T transaction(Work<T> work) throws Exception;
}
