package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @Override
    public ServiceResult<Map<String, CourseOfferingCapacitySnapshot>> snapshotForOfferings(
            List<CourseOffering> offerings) {
        if (offerings == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "offerings must not be null");
        }
        Map<String, CourseOffering> offeringsById = new LinkedHashMap<String, CourseOffering>();
        for (CourseOffering offering : offerings) {
            if (offering == null) {
                return ServiceResult.failure(StatusCode.BAD_REQUEST,
                        "offerings must not contain null");
            }
            offeringsById.put(offering.getOfferingId(), offering);
        }
        if (offeringsById.isEmpty()) {
            return ServiceResult.ok(Collections.<String, CourseOfferingCapacitySnapshot>emptyMap());
        }
        ServiceResult<List<CourseSelectionRecord>> recordsResult = records
                .listActiveByOfferingIds(new ArrayList<String>(offeringsById.keySet()));
        if (recordsResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(recordsResult.getStatus(), recordsResult.getMessage());
        }
        Map<String, int[]> usedByOffering = new LinkedHashMap<String, int[]>();
        for (String offeringId : offeringsById.keySet()) {
            usedByOffering.put(offeringId, new int[CapacityBucket.values().length]);
        }
        for (CourseSelectionRecord record : recordsResult.getData()) {
            int[] used = usedByOffering.get(record.getOfferingId());
            if (used != null) {
                used[record.getSelectionType().getCapacityBucket().ordinal()]++;
            }
        }
        Map<String, CourseOfferingCapacitySnapshot> snapshots =
                new LinkedHashMap<String, CourseOfferingCapacitySnapshot>();
        for (CourseOffering offering : offeringsById.values()) {
            int[] used = usedByOffering.get(offering.getOfferingId());
            snapshots.put(offering.getOfferingId(), snapshot(offering, used));
        }
        return ServiceResult.ok(Collections.unmodifiableMap(snapshots));
    }

    private static CourseOfferingCapacitySnapshot snapshot(CourseOffering offering, int[] used) {
        return new CourseOfferingCapacitySnapshot(offering.getOfferingId(),
                new CapacityBucketUsage(CapacityBucket.REQUIRED, offering.getRequiredCapacity(),
                        used[CapacityBucket.REQUIRED.ordinal()]),
                new CapacityBucketUsage(CapacityBucket.ELECTIVE, offering.getElectiveCapacity(),
                        used[CapacityBucket.ELECTIVE.ordinal()]),
                new CapacityBucketUsage(CapacityBucket.CROSS_MAJOR,
                        offering.getCrossMajorCapacity(),
                        used[CapacityBucket.CROSS_MAJOR.ordinal()]));
    }
}
