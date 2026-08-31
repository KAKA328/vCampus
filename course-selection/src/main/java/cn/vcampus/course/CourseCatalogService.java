package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** 教务人员维护全校课程目录的业务接口。 */
public interface CourseCatalogService {
    ServiceResult<Course> create(Course course);

    ServiceResult<Course> findById(String courseId);

    /** 查询可继续用于培养方案和教学班的启用课程。 */
    ServiceResult<Course> findActiveById(String courseId);

    ServiceResult<List<Course>> listAll();

    ServiceResult<List<Course>> listActive();

    ServiceResult<Course> updateDetails(String courseId, String name, int credits);

    ServiceResult<Course> changeStatus(String courseId, CourseStatus status);
}
