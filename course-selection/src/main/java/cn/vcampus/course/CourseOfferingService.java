package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/**
 * 教务人员维护教学班的业务接口。
 *
 * <p>权限由后续服务器消息处理器校验；本接口只负责教学班数据及其状态、容量规则。</p>
 */
public interface CourseOfferingService {
    ServiceResult<CourseOffering> create(CourseOffering offering);
    ServiceResult<CourseOffering> findById(String offeringId);
    ServiceResult<List<CourseOffering>> listByTerm(String term);
    ServiceResult<List<CourseOffering>> listByCourse(String courseId, String term);
    ServiceResult<List<CourseOffering>> listOpenByCourse(String courseId, String term);
    ServiceResult<CourseOffering> changeStatus(String offeringId, CourseOfferingStatus status);
    ServiceResult<CourseOffering> changeCapacities(String offeringId, int requiredCapacity,
            int electiveCapacity, int crossMajorCapacity);
}
