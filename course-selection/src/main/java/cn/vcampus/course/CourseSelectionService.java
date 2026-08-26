package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** Course selection contract. */
public interface CourseSelectionService {
    ServiceResult<List<Course>> listCourses();
    ServiceResult<Void> select(String studentId, String courseId);
    ServiceResult<Void> drop(String studentId, String courseId);
    ServiceResult<List<Course>> selectedCourses(String studentId);
}
