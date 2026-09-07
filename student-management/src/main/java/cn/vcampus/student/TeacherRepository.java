package cn.vcampus.student;

/** Persistence contract for teacher archives owned by student management. */
public interface TeacherRepository {
    default java.util.List<TeacherProfile> findAll() { throw new UnsupportedOperationException("teacher directory unavailable"); }
    TeacherProfile findById(String teacherId);
    TeacherProfile findByUserId(String userId);
    TeacherProfile save(TeacherProfile profile);
}
