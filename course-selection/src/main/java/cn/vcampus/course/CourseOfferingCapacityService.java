package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import java.util.List;
import java.util.Map;

/**
 * 计算教学班三个容量池实际使用情况的接口。
 */
public interface CourseOfferingCapacityService {
    ServiceResult<CourseOfferingCapacitySnapshot> snapshotFor(String offeringId);

    /** 批量查询教学班容量快照，避免课程列表逐条访问数据源。 */
    ServiceResult<Map<String, CourseOfferingCapacitySnapshot>> snapshotForOfferings(
            List<CourseOffering> offerings);
}
