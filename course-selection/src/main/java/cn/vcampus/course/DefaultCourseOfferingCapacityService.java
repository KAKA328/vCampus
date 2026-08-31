package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/**
 * 基于教学班配置和当前有效选课记录的容量统计实现。
 *
 * <p>重修记录通过 {@link SelectionType#getCapacityBucket()} 自动计入必修容量，
 * 已退选记录不会占用任何容量。</p>
 */
public final class DefaultCourseOfferingCapacityService implements CourseOfferingCapacityService {
    private final CourseOfferingService offerings;
    private final CourseSelectionRecordService records;

    public DefaultCourseOfferingCapacityService(CourseOfferingService offerings,
            CourseSelectionRecordService records) {
        if (offerings == null || records == null) {
            throw new IllegalArgumentException("offering and record services must not be null");
        }
        this.offerings = offerings;
        this.records = records;
    }

    @Override
    public ServiceResult<CourseOfferingCapacitySnapshot> snapshotFor(String offeringId) {
        ServiceResult<CourseOffering> offeringResult = offerings.findById(offeringId);
        if (offeringResult.getStatus() != cn.vcampus.common.StatusCode.OK) {
            return ServiceResult.failure(offeringResult.getStatus(), offeringResult.getMessage());
        }
        ServiceResult<List<CourseSelectionRecord>> recordsResult = records.listActiveByOffering(
                offeringResult.getData().getOfferingId());
        if (recordsResult.getStatus() != cn.vcampus.common.StatusCode.OK) {
            return ServiceResult.failure(recordsResult.getStatus(), recordsResult.getMessage());
        }

        int requiredUsed = 0;
        int electiveUsed = 0;
        int crossMajorUsed = 0;
        for (CourseSelectionRecord record : recordsResult.getData()) {
            switch (record.getSelectionType().getCapacityBucket()) {
                case REQUIRED:
                    requiredUsed++;
                    break;
                case ELECTIVE:
                    electiveUsed++;
                    break;
                case CROSS_MAJOR:
                    crossMajorUsed++;
                    break;
                default:
                    throw new IllegalStateException("unsupported capacity bucket");
            }
        }

        CourseOffering offering = offeringResult.getData();
        CourseOfferingCapacitySnapshot snapshot = new CourseOfferingCapacitySnapshot(
                offering.getOfferingId(),
                new CapacityBucketUsage(CapacityBucket.REQUIRED, offering.getRequiredCapacity(),
                        requiredUsed),
                new CapacityBucketUsage(CapacityBucket.ELECTIVE, offering.getElectiveCapacity(),
                        electiveUsed),
                new CapacityBucketUsage(CapacityBucket.CROSS_MAJOR,
                        offering.getCrossMajorCapacity(), crossMajorUsed));
        return ServiceResult.ok(snapshot);
    }
}
