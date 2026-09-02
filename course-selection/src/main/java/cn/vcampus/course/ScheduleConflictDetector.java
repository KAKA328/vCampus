package cn.vcampus.course;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 比较两个教学班时间表，找出相互重叠的上课安排。
 */
public final class ScheduleConflictDetector {

    /** 返回两个时间表全部冲突安排；返回空列表表示可以同时选择。 */
    public List<ScheduleConflict> findConflicts(CourseSchedule existingSchedule,
            CourseSchedule candidateSchedule) {
        if (existingSchedule == null || candidateSchedule == null) {
            throw new IllegalArgumentException("schedules must not be null");
        }
        List<ScheduleConflict> conflicts = new ArrayList<ScheduleConflict>();
        for (CourseMeeting existingMeeting : existingSchedule.getMeetings()) {
            for (CourseMeeting candidateMeeting : candidateSchedule.getMeetings()) {
                if (existingMeeting.overlaps(candidateMeeting)) {
                    conflicts.add(new ScheduleConflict(existingMeeting, candidateMeeting));
                }
            }
        }
        return Collections.unmodifiableList(conflicts);
    }

    /** 判断两个时间表是否至少存在一个时间冲突。 */
    public boolean hasConflict(CourseSchedule existingSchedule, CourseSchedule candidateSchedule) {
        return !findConflicts(existingSchedule, candidateSchedule).isEmpty();
    }
}
