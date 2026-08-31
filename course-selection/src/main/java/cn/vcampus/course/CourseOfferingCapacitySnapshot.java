package cn.vcampus.course;

import java.io.Serializable;

/**
 * 某个教学班三个容量池在指定时刻的统计快照。
 */
public final class CourseOfferingCapacitySnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String offeringId;
    private final CapacityBucketUsage requiredUsage;
    private final CapacityBucketUsage electiveUsage;
    private final CapacityBucketUsage crossMajorUsage;

    public CourseOfferingCapacitySnapshot(String offeringId, CapacityBucketUsage requiredUsage,
            CapacityBucketUsage electiveUsage, CapacityBucketUsage crossMajorUsage) {
        if (offeringId == null || offeringId.trim().isEmpty()) {
            throw new IllegalArgumentException("offeringId must not be blank");
        }
        requireBucket(requiredUsage, CapacityBucket.REQUIRED);
        requireBucket(electiveUsage, CapacityBucket.ELECTIVE);
        requireBucket(crossMajorUsage, CapacityBucket.CROSS_MAJOR);
        this.offeringId = offeringId.trim();
        this.requiredUsage = requiredUsage;
        this.electiveUsage = electiveUsage;
        this.crossMajorUsage = crossMajorUsage;
    }

    public String getOfferingId() {
        return offeringId;
    }

    public CapacityBucketUsage getRequiredUsage() {
        return requiredUsage;
    }

    public CapacityBucketUsage getElectiveUsage() {
        return electiveUsage;
    }

    public CapacityBucketUsage getCrossMajorUsage() {
        return crossMajorUsage;
    }

    /** 按容量池取得对应的统计结果。 */
    public CapacityBucketUsage getUsage(CapacityBucket capacityBucket) {
        if (capacityBucket == null) {
            throw new IllegalArgumentException("capacityBucket must not be null");
        }
        switch (capacityBucket) {
            case REQUIRED:
                return requiredUsage;
            case ELECTIVE:
                return electiveUsage;
            case CROSS_MAJOR:
                return crossMajorUsage;
            default:
                throw new IllegalArgumentException("unsupported capacity bucket");
        }
    }

    private static void requireBucket(CapacityBucketUsage usage, CapacityBucket expectedBucket) {
        if (usage == null || usage.getCapacityBucket() != expectedBucket) {
            throw new IllegalArgumentException("capacity usage must match " + expectedBucket);
        }
    }
}
