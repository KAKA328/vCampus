package cn.vcampus.client.view;

import cn.vcampus.common.Role;
import cn.vcampus.common.User;
import cn.vcampus.user.Session;
import java.lang.reflect.Field;
import javax.swing.JButton;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentManagementPanelTest {
    @Test
    void studentPanelAllowsSelfQueryAndContactEditOnly() throws Exception {
        StudentManagementPanel panel = new StudentManagementPanel("127.0.0.1", 1,
                new Session("token", new User("student-user", "学生", Role.STUDENT)));

        assertTrue(field(panel, "selfButton", JButton.class).isEnabled());
        assertFalse(field(panel, "idButton", JButton.class).isEnabled());
        assertFalse(field(panel, "classButton", JButton.class).isEnabled());
        assertTrue(field(panel, "phone", JTextField.class).isEditable());
        assertTrue(field(panel, "email", JTextField.class).isEditable());
        assertFalse(field(panel, "name", JTextField.class).isEditable());
        assertFalse(field(panel, "academicStatus", JTextField.class).isEditable());
    }

    @Test
    void academicAdministratorCanQueryAndSaveProfiles() throws Exception {
        StudentManagementPanel panel = new StudentManagementPanel("127.0.0.1", 1,
                new Session("token", new User("academic-admin", "教务管理员", Role.ACADEMIC_ADMIN)));

        assertTrue(field(panel, "idButton", JButton.class).isEnabled());
        assertTrue(field(panel, "classButton", JButton.class).isEnabled());
        assertTrue(field(panel, "saveButton", JButton.class).isEnabled());
        assertTrue(field(panel, "academicStatus", JTextField.class).isEditable());
        assertTrue(field(panel, "studentId", JTextField.class).isEditable());
    }

    @Test
    void mainFrameRecognizesAllStudentModuleTitles() {
        ModuleNavigationModel model = new ModuleNavigationModel();
        assertTrue(MainFrame.useStudentManagementPanel(Role.STUDENT,
                model.findModule(Role.STUDENT, "学籍信息")));
        assertTrue(MainFrame.useStudentManagementPanel(Role.TEACHER,
                model.findModule(Role.TEACHER, "学籍查询")));
        assertTrue(MainFrame.useStudentManagementPanel(Role.ADMIN,
                model.findModule(Role.ADMIN, "学籍管理")));
        assertTrue(MainFrame.useStudentManagementPanel(Role.ACADEMIC_ADMIN,
                model.findModule(Role.ACADEMIC_ADMIN, "学籍管理")));
    }

    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }
}
