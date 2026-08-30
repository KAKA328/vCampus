package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;

/**
 * 计算教学班三个容量池实际使用情况的接口。
 */
public interface CourseOfferingCapacityService {
    ServiceResult<CourseOfferingCapacitySnapshot> snapshotFor(String offeringId);
}
