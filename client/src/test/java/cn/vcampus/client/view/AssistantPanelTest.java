package cn.vcampus.client.view;

import cn.vcampus.common.Role;
import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.util.Arrays;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantPanelTest {
    @Test
    void assistantUsesTheUnifiedEntryStyleAndShowsShortcutQuestions() {
        AssistantPanel panel = new AssistantPanel(Role.ADMIN, Arrays.asList(
                new ModuleDescriptor("用户管理", "维护账号。", "可用：已接入")), module -> { });

        JButton avatar = findButtonByTooltip(panel, "打开小松鼠助手");
        assertNotNull(avatar);
        assertEquals(VCampusTheme.PRIMARY, avatar.getBackground());

        avatar.doClick();

        assertTrue(labels(panel).contains("小松鼠助手"));
        assertTrue(labels(panel).contains("当前身份：系统管理员"));
        assertTrue(labels(panel).contains("常用问题"));
        assertNotNull(findButton(panel, "我想选课"));
    }

    @Test
    void assistantWrapsEveryShortcutInsideTheVisibleAnswerWidth() {
        AssistantPanel panel = new AssistantPanel(Role.ADMIN, Arrays.asList(
                new ModuleDescriptor("用户管理", "维护账号。", "可用：已接入")), module -> { });
        panel.resizeForWindow(UiMetrics.px(1180), UiMetrics.px(760));
        panel.setSize(panel.getPreferredSize());
        findButtonByTooltip(panel, "打开小松鼠助手").doClick();

        layoutRecursively(panel);

        JButton lastShortcut = findButton(panel, "哪些功能当前可用");
        assertNotNull(lastShortcut);
        Container shortcuts = lastShortcut.getParent();
        assertTrue(shortcuts.getWidth() > 0);
        assertTrue(lastShortcut.getX() + lastShortcut.getWidth() <= shortcuts.getWidth(),
                "the last shortcut must wrap instead of being clipped on the right");
        JScrollPane answerScroll = findScrollPane(panel);
        assertNotNull(answerScroll);
        Component answerView = answerScroll.getViewport().getView();
        assertTrue(answerView instanceof Scrollable);
        assertTrue(((Scrollable) answerView).getScrollableTracksViewportWidth(),
                "assistant answers must track the viewport width so shortcuts can wrap");
        assertTrue(answerView.getPreferredSize().height
                        <= answerScroll.getViewport().getExtentSize().height,
                "the default welcome and shortcut questions should be visible without scrolling");
        assertButtonTextFits(findButton(panel, "发送"));
        assertButtonTextFits(findButton(panel, "×"));
    }

    @Test
    void administratorQuestionsRouteToUserManagement() {
        assertRoutes(Role.ADMIN, "忘记密码怎么重置", "用户管理");
        assertRoutes(Role.ADMIN, "在哪里查看操作日志和审计记录", "用户管理");
        assertRoutes(Role.ADMIN, "批量导入账号并绑定档案", "用户管理");
    }

    @Test
    void studentQuestionsCoverStudyLibraryAndStoreVocabulary() {
        assertRoutes(Role.STUDENT, "我的手机号和邮箱在哪里修改", "学籍信息");
        assertRoutes(Role.STUDENT, "怎么查看重修课程和培养方案", "学生选课");
        assertRoutes(Role.STUDENT, "图书逾期、挂失和赔偿怎么办", "图书馆");
        assertRoutes(Role.STUDENT, "在哪里查看钱包流水和订单", "商店");
    }

    @Test
    void teacherQuestionsDistinguishProfileFromTeachingWork() {
        assertRoutes(Role.TEACHER, "查看教师工号、职称和在职状态", "教师信息");
        assertRoutes(Role.TEACHER, "录入成绩、导入成绩单并查看教学班", "教学管理");
    }

    @Test
    void academicAdministratorQuestionsDistinguishStudentAndCourseManagement() {
        assertRoutes(Role.ACADEMIC_ADMIN, "办理毕业审查和核对学分", "学籍管理");
        assertRoutes(Role.ACADEMIC_ADMIN, "维护培养方案、选课轮次和成绩审核", "教务教学管理");
    }

    @Test
    void storeAndLibraryManagerQuestionsUseTheirManagementEntries() {
        assertRoutes(Role.STORE_MANAGER, "商品补货、上下架和余额校正", "商店管理");
        assertRoutes(Role.STORE_MANAGER, "购买商品并查看我的订单", "商店");
        assertRoutes(Role.LIBRARIAN, "处理借阅、逾期、挂失和赔偿", "图书馆");
    }

    private static void assertRoutes(Role role, String question, String moduleTitle) {
        AssistantPanel panel = new AssistantPanel(role,
                new ModuleNavigationModel().visibleModuleCards(role), module -> { });
        findButtonByTooltip(panel, "打开小松鼠助手").doClick();
        JTextField input = findTextField(panel);
        assertNotNull(input);
        input.setText(question);
        input.postActionEvent();
        assertNotNull(findButton(panel, "打开“" + moduleTitle + "”"),
                () -> role + " question should route to " + moduleTitle + ": " + question);
    }

    private static JButton findButtonByTooltip(Component component, String tooltip) {
        if (component instanceof JButton && tooltip.equals(((JButton) component).getToolTipText())) {
            return (JButton) component;
        }
        return findButton(component, null, tooltip);
    }

    private static JButton findButton(Component component, String text) {
        return findButton(component, text, null);
    }

    private static JButton findButton(Component component, String text, String tooltip) {
        if (component instanceof JButton) {
            JButton button = (JButton) component;
            if ((text == null || text.equals(button.getText()))
                    && (tooltip == null || tooltip.equals(button.getToolTipText()))) {
                return button;
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JButton found = findButton(child, text, tooltip);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static java.util.List<String> labels(Component component) {
        java.util.List<String> values = new java.util.ArrayList<String>();
        collectLabels(component, values);
        return values;
    }

    private static void collectLabels(Component component, java.util.List<String> values) {
        if (component instanceof JLabel) values.add(((JLabel) component).getText());
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collectLabels(child, values);
            }
        }
    }

    private static void layoutRecursively(Component component) {
        if (!(component instanceof Container)) return;
        Container container = (Container) component;
        container.doLayout();
        for (Component child : container.getComponents()) {
            layoutRecursively(child);
        }
    }

    private static JScrollPane findScrollPane(Component component) {
        if (component instanceof JScrollPane) return (JScrollPane) component;
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JScrollPane found = findScrollPane(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JTextField findTextField(Component component) {
        if (component instanceof JTextField) return (JTextField) component;
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JTextField found = findTextField(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void assertButtonTextFits(JButton button) {
        assertNotNull(button);
        Insets insets = button.getInsets();
        int requiredWidth = button.getFontMetrics(button.getFont()).stringWidth(button.getText())
                + insets.left + insets.right;
        assertTrue(button.getPreferredSize().width >= requiredWidth,
                button.getText() + " button must not render as an ellipsis");
    }
}
