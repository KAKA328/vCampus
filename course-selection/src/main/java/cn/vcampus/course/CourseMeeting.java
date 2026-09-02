package cn.vcampus.course;

import java.io.Serializable;
import java.time.DayOfWeek;

/**
 * 教学班的一次具体上课安排。
 *
 * <p>节次区间包含开始和结束节次。例如“第 1 至第 2 节”与“第 2 至第 3 节”共享第 2 节，
 * 因而会被视为时间冲突。</p>
 */
public final class CourseMeeting implements Serializable {
    private static final long serialVersionUID = 1L;

    private final DayOfWeek dayOfWeek;
    private final int startPeriod;
    private final int endPeriod;
    private final String location;

    public CourseMeeting(DayOfWeek dayOfWeek, int startPeriod, int endPeriod, String location) {
        if (dayOfWeek == null) {
            throw new IllegalArgumentException("dayOfWeek must not be null");
        }
        if (startPeriod < 1 || endPeriod < startPeriod) {
            throw new IllegalArgumentException("period range is invalid");
        }
        if (location == null || location.trim().isEmpty()) {
            throw new IllegalArgumentException("location must not be blank");
        }
        this.dayOfWeek = dayOfWeek;
        this.startPeriod = startPeriod;
        this.endPeriod = endPeriod;
        this.location = location.trim();
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public int getStartPeriod() {
        return startPeriod;
    }

    public int getEndPeriod() {
        return endPeriod;
    }

    public String getLocation() {
        return location;
    }

    /**
     * 判断两个上课安排是否占用同一天的至少一个相同节次。
     */
    public boolean overlaps(CourseMeeting other) {
        if (other == null) {
            throw new IllegalArgumentException("other meeting must not be null");
        }
        return dayOfWeek == other.dayOfWeek
                && startPeriod <= other.endPeriod
                && other.startPeriod <= endPeriod;
    }
}
