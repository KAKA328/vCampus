package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** Course selection contract. */
public interface CourseSelectionService {
    ServiceResult<List<Course>> listCourses();
    ServiceResult<Void> select(String studentId, String courseId);
    ServiceResult<Void> drop(String studentId, String courseId);
    ServiceResult<List<Course>> selectedCourses(String studentId);
    ServiceResult<Course> findCourse(String courseId);
    ServiceResult<Void> createCourse(Course course);
    ServiceResult<Void> updateCourse(Course course);
    ServiceResult<Void> deactivateCourse(String courseId);
    ServiceResult<Void> recordGrade(String teacherId, String studentId, String courseId, int score);
    ServiceResult<Integer> gradeOf(String studentId, String courseId);
}
