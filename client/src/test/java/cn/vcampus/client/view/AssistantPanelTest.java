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

    private static void assertButtonTextFits(JButton button) {
        assertNotNull(button);
        Insets insets = button.getInsets();
        int requiredWidth = button.getFontMetrics(button.getFont()).stringWidth(button.getText())
                + insets.left + insets.right;
        assertTrue(button.getPreferredSize().width >= requiredWidth,
                button.getText() + " button must not render as an ellipsis");
    }
}
