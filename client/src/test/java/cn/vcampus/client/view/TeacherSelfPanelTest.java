package cn.vcampus.client.view;

import cn.vcampus.common.*;
import cn.vcampus.student.TeacherProfile;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TeacherSelfPanelTest {
    @Test void teacherEntryContainsOnlySelfPanel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            StudentManagementPanel panel = new StudentManagementPanel("127.0.0.1", 1,
                    new Session("token", new User("teacher", "教师", Role.TEACHER)));
            assertEquals(1, panel.getComponentCount());
            assertTrue(((BorderLayout) panel.getLayout()).getLayoutComponent(BorderLayout.CENTER)
                    instanceof TeacherSelfPanel);
        });
    }

    @Test void fieldsAreReadOnlyShowInactiveAndClearOnFailure() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                TeacherSelfPanel panel = new TeacherSelfPanel(() -> null);
                java.lang.reflect.Field field = panel.getClass().getDeclaredField("values");
                field.setAccessible(true);
                JTextField[] values = (JTextField[]) field.get(panel);
                Message request = Message.request("self", MessageType.TEACHER_SELF_QUERY_V1, null);
                panel.showResponse(Message.response(request, StatusCode.OK,
                        new TeacherProfile("T001", "teacher", "教师", "院系", "讲师", false)));
                assertEquals("T001", values[0].getText());
                assertEquals("非在职", values[5].getText());
                for (JTextField value : values) assertFalse(value.isEditable());
                panel.showResponse(Message.response(request, StatusCode.NOT_FOUND, null));
                for (JTextField value : values) assertEquals("", value.getText());
            } catch (Exception failure) { throw new RuntimeException(failure); }
        });
    }
}
