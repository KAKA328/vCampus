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
    private final int startWeek;
    private final int endWeek;
    private final String location;

    public CourseMeeting(DayOfWeek dayOfWeek, int startPeriod, int endPeriod, String location) {
        this(dayOfWeek, startPeriod, endPeriod, 1, 20, location);
    }

    /** 创建一个在指定教学周范围内生效的上课安排。 */
    public CourseMeeting(DayOfWeek dayOfWeek, int startPeriod, int endPeriod, int startWeek,
            int endWeek, String location) {
        if (dayOfWeek == null) {
            throw new IllegalArgumentException("dayOfWeek must not be null");
        }
        if (startPeriod < 1 || endPeriod < startPeriod) {
            throw new IllegalArgumentException("period range is invalid");
        }
        if (startWeek < 1 || endWeek < startWeek) {
            throw new IllegalArgumentException("week range is invalid");
        }
        if (location == null || location.trim().isEmpty()) {
            throw new IllegalArgumentException("location must not be blank");
        }
        this.dayOfWeek = dayOfWeek;
        this.startPeriod = startPeriod;
        this.endPeriod = endPeriod;
        this.startWeek = startWeek;
        this.endWeek = endWeek;
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

    public int getStartWeek() { return startWeek; }

    public int getEndWeek() { return endWeek; }

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
                && startWeek <= other.endWeek
                && other.startWeek <= endWeek
                && startPeriod <= other.endPeriod
                && other.startPeriod <= endPeriod;
    }
}
