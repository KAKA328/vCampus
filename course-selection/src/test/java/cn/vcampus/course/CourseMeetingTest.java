package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import org.junit.jupiter.api.Test;

class CourseMeetingTest {

    @Test
    void detectsOverlappingPeriodsOnSameDay() {
        CourseMeeting first = new CourseMeeting(DayOfWeek.MONDAY, 1, 2, "A201");
        CourseMeeting overlapping = new CourseMeeting(DayOfWeek.MONDAY, 2, 3, "B101");
        CourseMeeting differentDay = new CourseMeeting(DayOfWeek.TUESDAY, 1, 2, "A201");

        assertTrue(first.overlaps(overlapping));
        assertFalse(first.overlaps(differentDay));
    }

    @Test
    void rejectsInvalidMeetingInformation() {
        assertThrows(IllegalArgumentException.class,
                () -> new CourseMeeting(null, 1, 2, "A201"));
        assertThrows(IllegalArgumentException.class,
                () -> new CourseMeeting(DayOfWeek.MONDAY, 0, 2, "A201"));
        assertThrows(IllegalArgumentException.class,
                () -> new CourseMeeting(DayOfWeek.MONDAY, 3, 2, "A201"));
        assertThrows(IllegalArgumentException.class,
                () -> new CourseMeeting(DayOfWeek.MONDAY, 1, 2, " "));
    }
}
