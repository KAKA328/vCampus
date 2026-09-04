package cn.vcampus.student;

/** Persistence contract for teacher archives owned by student management. */
public interface TeacherRepository {
    TeacherProfile findById(String teacherId);
    TeacherProfile findByUserId(String userId);
    TeacherProfile save(TeacherProfile profile);
}
