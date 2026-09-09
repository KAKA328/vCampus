package cn.vcampus.client.view;

import cn.vcampus.common.*;
import cn.vcampus.student.StudentRecord;
import cn.vcampus.user.Session;
import java.lang.reflect.*;
import javax.swing.*;
import javax.swing.text.AbstractDocument;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StudentProfileFormValidationTest {
    @Test void bothRolesFilterPhonePasteAndRejectIncompleteNumbersOnSave() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                for (Role role : new Role[] {Role.STUDENT, Role.ACADEMIC_ADMIN}) {
                    StudentManagementPanel panel = panel(role);
                    load(panel, "在读", "13800000000");
                    JTextField phone = field(panel, "phone", JTextField.class);
                    phone.setText("");
                    AbstractDocument document = (AbstractDocument) phone.getDocument();
                    document.replace(0, 0, "abc", null);
                    assertEquals("", phone.getText());
                    document.replace(0, 0, "138000000001", null);
                    assertEquals("", phone.getText());
                    phone.setText("1380000000");
                    InvocationTargetException incomplete = assertThrows(InvocationTargetException.class,
                            () -> method("readRecord").invoke(panel));
                    assertTrue(incomplete.getCause().getMessage().contains("11位"));
                    phone.setText("13900000001");
                    StudentRecord result = (StudentRecord) method("readRecord").invoke(panel);
                    assertEquals("13900000001", result.getPhone());
                    assertEquals("在读", result.getStatus());
                }
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
    @Test void statusAndGenderAreFixedChoicesAndGraduationIsReadOnly() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                StudentManagementPanel panel = panel(Role.ACADEMIC_ADMIN);
                load(panel, "在读", null);
                JComboBox<?> status = field(panel, "academicStatus", JComboBox.class);
                assertFalse(status.isEditable());
                assertEquals(3, status.getItemCount());
                status.setSelectedItem("随便输入");
                assertEquals("在读", status.getSelectedItem());
                status.setSelectedItem("休学");
                assertEquals("休学", ((StudentRecord) method("readRecord").invoke(panel)).getStatus());
                assertFalse(field(panel, "gender", JComboBox.class).isEditable());
                assertFalse(field(panel, "studentId", JTextField.class).isEditable());
                assertFalse(field(panel, "userId", JTextField.class).isEditable());
                load(panel, "毕业", null);
                assertEquals("毕业", status.getSelectedItem());
                assertFalse(status.isEnabled());
                load(panel, "历史异常状态", null);
                assertNull(status.getSelectedItem());
                assertThrows(InvocationTargetException.class, () -> method("readRecord").invoke(panel));
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
    @Test void invalidStoredContactIsVisibleRatherThanSilentlyCleared() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                StudentManagementPanel panel = panel(Role.STUDENT);
                load(panel, "在读", "旧的错误号码");
                assertEquals("旧的错误号码", field(panel, "phone", JTextField.class).getText());
                assertThrows(InvocationTargetException.class, () -> method("readRecord").invoke(panel));
                field(panel, "phone", JTextField.class).setText("13800000000");
                field(panel, "email", JTextField.class).setText("wrong");
                assertThrows(InvocationTargetException.class, () -> method("readRecord").invoke(panel));
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
    private static StudentManagementPanel panel(Role role) {
        return new StudentManagementPanel("127.0.0.1", 1, new Session("token", new User("student", "测试", role)));
    }
    private static void load(StudentManagementPanel panel, String state, String phone) throws Exception {
        StudentRecord profile = new StudentRecord("S001", "student", "学生", "男", "院系", "专业", "班级", 2026, state, phone, null);
        Method show = StudentManagementPanel.class.getDeclaredMethod("showSingle", Message.class, String.class);
        show.setAccessible(true);
        show.invoke(panel, Message.response(Message.request("load", MessageType.STUDENT_QUERY, null), StatusCode.OK, profile), "成功");
        method("updateButtons").invoke(panel);
    }
    private static Method method(String name) throws Exception {
        Method method = StudentManagementPanel.class.getDeclaredMethod(name); method.setAccessible(true); return method;
    }
    private static <T> T field(Object owner, String name, Class<T> type) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return type.cast(field.get(owner));
    }
}
