package cn.vcampus.course;

import java.io.Serializable;

/**
 * 教学班某一个容量池的容量使用情况。
 */
public final class CapacityBucketUsage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final CapacityBucket capacityBucket;
    private final int totalCapacity;
    private final int usedCapacity;

    public CapacityBucketUsage(CapacityBucket capacityBucket, int totalCapacity, int usedCapacity) {
        if (capacityBucket == null) {
            throw new IllegalArgumentException("capacityBucket must not be null");
        }
        if (totalCapacity < 0 || usedCapacity < 0) {
            throw new IllegalArgumentException("capacity values must not be negative");
        }
        this.capacityBucket = capacityBucket;
        this.totalCapacity = totalCapacity;
        this.usedCapacity = usedCapacity;
    }

    public CapacityBucket getCapacityBucket() {
        return capacityBucket;
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    public int getUsedCapacity() {
        return usedCapacity;
    }

    /** 返回可继续选课的名额；出现历史数据超额时最低显示为 0。 */
    public int getRemainingCapacity() {
        return Math.max(0, totalCapacity - usedCapacity);
    }

    /** 返回是否已经没有可继续分配的名额。 */
    public boolean isFull() {
        return usedCapacity >= totalCapacity;
    }

    /** 返回历史记录是否已经超过配置容量，供教务人员处理异常数据。 */
    public boolean isOverCapacity() {
        return usedCapacity > totalCapacity;
    }
}
