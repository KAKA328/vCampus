package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import java.math.BigDecimal;
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

    /** Precise-credit API. Legacy implementations reject fractional values instead of truncating. */
    default ServiceResult<Course> updateDetailsDecimal(String courseId, String name,
            BigDecimal credits) {
        if (credits == null) {
            return ServiceResult.failure(cn.vcampus.common.StatusCode.BAD_REQUEST,
                    "credits must not be null");
        }
        try {
            return updateDetails(courseId, name, credits.intValueExact());
        } catch (ArithmeticException fractionalOrOutOfRange) {
            return ServiceResult.failure(cn.vcampus.common.StatusCode.BAD_REQUEST,
                    "course catalog implementation does not support fractional credits");
        }
    }

    /** 更新课程编号、名称、学分与状态。课程编号变更时必须同步维护关联数据。 */
    ServiceResult<Course> updateDetails(String originalCourseId, Course course);

    ServiceResult<Course> changeStatus(String courseId, CourseStatus status);
}
