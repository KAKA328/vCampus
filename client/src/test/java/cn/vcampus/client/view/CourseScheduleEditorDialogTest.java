package cn.vcampus.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.course.CourseMeeting;
import cn.vcampus.course.CourseSchedule;
import java.time.DayOfWeek;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** 验证结构化排课按周次合并，列表展示与冲突检测使用同一份时间表。 */
class CourseScheduleEditorDialogTest {
    @Test
    void formatsMeetingsByWeekRangeInChinese() {
        CourseSchedule schedule = new CourseSchedule(Arrays.asList(
                new CourseMeeting(DayOfWeek.WEDNESDAY, 3, 4, 1, 8, "教学楼A201"),
                new CourseMeeting(DayOfWeek.TUESDAY, 5, 6, 9, 16, "教学楼A201"),
                new CourseMeeting(DayOfWeek.MONDAY, 1, 2, 1, 8, "教学楼A201")));

        assertEquals("1-8周，周一1-2节，周三3-4节；9-16周，周二5-6节",
                CourseScheduleEditorDialog.format(schedule));
    }
}
