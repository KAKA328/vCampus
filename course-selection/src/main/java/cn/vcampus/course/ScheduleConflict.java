package cn.vcampus.course;

import java.io.Serializable;

/**
 * 两个教学班时间表中发现的一组冲突上课安排。
 */
public final class ScheduleConflict implements Serializable {
    private static final long serialVersionUID = 1L;

    private final CourseMeeting existingMeeting;
    private final CourseMeeting candidateMeeting;

    public ScheduleConflict(CourseMeeting existingMeeting, CourseMeeting candidateMeeting) {
        if (existingMeeting == null || candidateMeeting == null) {
            throw new IllegalArgumentException("conflicting meetings must not be null");
        }
        if (!existingMeeting.overlaps(candidateMeeting)) {
            throw new IllegalArgumentException("meetings must overlap to form a conflict");
        }
        this.existingMeeting = existingMeeting;
        this.candidateMeeting = candidateMeeting;
    }

    public CourseMeeting getExistingMeeting() {
        return existingMeeting;
    }

    public CourseMeeting getCandidateMeeting() {
        return candidateMeeting;
    }
}
