package cn.vcampus.course;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一个教学班在一周内的全部上课安排。
 */
public final class CourseSchedule implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final CourseSchedule EMPTY = new CourseSchedule(Collections.<CourseMeeting>emptyList());

    private final List<CourseMeeting> meetings;

    public CourseSchedule(List<CourseMeeting> meetings) {
        if (meetings == null) {
            throw new IllegalArgumentException("meetings must not be null");
        }
        List<CourseMeeting> copiedMeetings = new ArrayList<CourseMeeting>(meetings);
        for (CourseMeeting meeting : copiedMeetings) {
            if (meeting == null) {
                throw new IllegalArgumentException("meetings must not contain null");
            }
        }
        validateNoInternalConflict(copiedMeetings);
        this.meetings = Collections.unmodifiableList(copiedMeetings);
    }

    /** 返回尚未排定具体上课时间时使用的空时间表。 */
    public static CourseSchedule empty() {
        return EMPTY;
    }

    public List<CourseMeeting> getMeetings() {
        return meetings;
    }

    public boolean isEmpty() {
        return meetings.isEmpty();
    }

    private static void validateNoInternalConflict(List<CourseMeeting> meetings) {
        for (int firstIndex = 0; firstIndex < meetings.size(); firstIndex++) {
            for (int secondIndex = firstIndex + 1; secondIndex < meetings.size(); secondIndex++) {
                if (meetings.get(firstIndex).overlaps(meetings.get(secondIndex))) {
                    throw new IllegalArgumentException("meetings in one schedule must not overlap");
                }
            }
        }
    }
}
