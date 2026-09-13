package cn.vcampus.client.view;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.Role;
import cn.vcampus.common.User;
import cn.vcampus.course.CourseMeeting;
import cn.vcampus.course.CourseOffering;
import cn.vcampus.course.CourseOfferingStatus;
import cn.vcampus.course.CourseSchedule;
import cn.vcampus.user.Session;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;

class CourseSelectionPanelTest {
    @Test
    void presentsRoundCardsInsteadOfLegacyDropdown() throws Exception {
        CourseSelectionPanel panel = new CourseSelectionPanel("localhost", 19090,
                new Session("token", new User("student-001", "测试学生", Role.STUDENT)));
        Field field = CourseSelectionPanel.class.getDeclaredField("roundCards");
        field.setAccessible(true);
        assertNotNull(field.get(panel));
        assertTrue(field.get(panel) instanceof JPanel);
    }

    @Test
    void disablesAllCourseActionsWhileRequestIsInProgress() throws Exception {
        CourseSelectionPanel panel = new CourseSelectionPanel("localhost", 19090,
                new Session("token", new User("student-001", "测试学生", Role.STUDENT)));
        Field busy = CourseSelectionPanel.class.getDeclaredField("requestInProgress");
        busy.setAccessible(true);
        busy.setBoolean(panel, true);
        Method update = CourseSelectionPanel.class.getDeclaredMethod("updateInteractiveState");
        update.setAccessible(true);
        update.invoke(panel);

        assertFalse(button(panel, "selectedCoursesButton").isEnabled());
        assertFalse(button(panel, "backToRoundsButton").isEnabled());
        assertFalse(button(panel, "backToCoursesButton").isEnabled());
        assertFalse(button(panel, "dropButton").isEnabled());

        busy.setBoolean(panel, false);
        update.invoke(panel);
        assertFalse(button(panel, "selectedCoursesButton").isEnabled());
    }

    @Test
    void appliesSharedThemeToCourseActions() throws Exception {
        CourseSelectionPanel panel = new CourseSelectionPanel("localhost", 19090,
                new Session("token", new User("student-001", "测试学生", Role.STUDENT)));
        assertTrue(button(panel, "selectedCoursesButton").getUI() instanceof VCampusTheme.ReadableButtonUI);
        assertTrue(button(panel, "backToRoundsButton").getUI() instanceof VCampusTheme.ReadableButtonUI);
        assertTrue(button(panel, "backToCoursesButton").getUI() instanceof VCampusTheme.ReadableButtonUI);
        assertTrue(button(panel, "dropButton").getUI() instanceof VCampusTheme.ReadableButtonUI);
    }

    @Test
    void presentsStudentFlowAsRoundSelectionFirst() throws Exception {
        CourseSelectionPanel panel = new CourseSelectionPanel("localhost", 19090,
                new Session("token", new User("student-001", "测试学生", Role.STUDENT)));

        assertTrue(label(panel, "pageSubtitle").getText().contains("请选择一个当前开放的选课轮次"));
        assertFalse(button(panel, "selectedCoursesButton").isEnabled());
    }

    @Test
    void opensTeachingClassesBySelectingACourseWithoutAnExtraActionButton() throws Exception {
        CourseSelectionPanel panel = new CourseSelectionPanel("localhost", 19090,
                new Session("token", new User("student-001", "测试学生", Role.STUDENT)));

        try {
            CourseSelectionPanel.class.getDeclaredField("courseDetailButton");
            throw new AssertionError("课程列表不应保留额外的查看教学班按钮");
        } catch (NoSuchFieldException expected) {
            assertTrue(true);
        }
    }

    @Test
    void keepsCourseTableColumnsReadableOnCompactWindows() throws Exception {
        CourseSelectionPanel panel = new CourseSelectionPanel("localhost", 19090,
                new Session("token", new User("student-001", "测试学生", Role.STUDENT)));
        Field field = CourseSelectionPanel.class.getDeclaredField("courseTable");
        field.setAccessible(true);
        JTable table = (JTable) field.get(panel);

        assertTrue(table.getAutoResizeMode() == JTable.AUTO_RESIZE_OFF);
        assertTrue(table.getColumnModel().getColumn(0).getPreferredWidth() >= UiMetrics.px(150));
        assertTrue(table.getColumnModel().getColumn(1).getPreferredWidth() >= UiMetrics.px(260));
    }

    @Test
    void listsTheLocationForEachStructuredMeeting() {
        CourseOffering offering = new CourseOffering("OFFER-001", "CS101", "2026-2027-1",
                "教师001", "1-8周，周一1-2节；9-16周，周三3-4节", "教学楼A201", 30, 5, 5,
                CourseOfferingStatus.OPEN).withMeetingSchedule(new CourseSchedule(Arrays.asList(
                        new CourseMeeting(DayOfWeek.MONDAY, 1, 2, 1, 8, "教学楼A201"),
                        new CourseMeeting(DayOfWeek.WEDNESDAY, 3, 4, 9, 16, "教学楼B302"))));

        assertEquals("1-8周，周一1-2节：教学楼A201；9-16周，周三3-4节：教学楼B302",
                CourseSelectionPanel.meetingLocationSummary(offering));
    }

    private static AbstractButton button(CourseSelectionPanel panel, String fieldName) throws Exception {
        Field field = CourseSelectionPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (AbstractButton) field.get(panel);
    }

    private static JLabel label(CourseSelectionPanel panel, String fieldName) throws Exception {
        Field field = CourseSelectionPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (JLabel) field.get(panel);
    }
}
