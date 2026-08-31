package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ScheduleConflictDetectorTest {

    @Test
    void findsConflictingMeetingsBetweenTwoSchedules() {
        CourseSchedule selectedSchedule = new CourseSchedule(Arrays.asList(
                new CourseMeeting(DayOfWeek.MONDAY, 1, 2, "A201"),
                new CourseMeeting(DayOfWeek.WEDNESDAY, 5, 6, "A201")));
        CourseSchedule candidateSchedule = new CourseSchedule(Arrays.asList(
                new CourseMeeting(DayOfWeek.MONDAY, 2, 3, "B101"),
                new CourseMeeting(DayOfWeek.TUESDAY, 1, 2, "B101")));
        ScheduleConflictDetector detector = new ScheduleConflictDetector();

        assertTrue(detector.hasConflict(selectedSchedule, candidateSchedule));
        assertEquals(1, detector.findConflicts(selectedSchedule, candidateSchedule).size());
    }

    @Test
    void allowsSchedulesOnDifferentDaysOrPeriods() {
        CourseSchedule selectedSchedule = new CourseSchedule(Arrays.asList(
                new CourseMeeting(DayOfWeek.MONDAY, 1, 2, "A201")));
        CourseSchedule candidateSchedule = new CourseSchedule(Arrays.asList(
                new CourseMeeting(DayOfWeek.MONDAY, 3, 4, "B101"),
                new CourseMeeting(DayOfWeek.TUESDAY, 1, 2, "B101")));
        ScheduleConflictDetector detector = new ScheduleConflictDetector();

        assertFalse(detector.hasConflict(selectedSchedule, candidateSchedule));
    }

    @Test
    void rejectsOverlappingMeetingsInsideOneSchedule() {
        assertThrows(IllegalArgumentException.class,
                () -> new CourseSchedule(Arrays.asList(
                        new CourseMeeting(DayOfWeek.MONDAY, 1, 2, "A201"),
                        new CourseMeeting(DayOfWeek.MONDAY, 2, 3, "A201"))));
    }
}
