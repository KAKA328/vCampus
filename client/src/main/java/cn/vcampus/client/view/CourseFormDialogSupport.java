package cn.vcampus.client.view;

import cn.vcampus.course.CourseStatus;
import cn.vcampus.course.SelectionRoundType;
import cn.vcampus.course.SelectionType;
import java.awt.Component;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/** 选课管理表单弹窗共用的尺寸、滚动和中文枚举展示规则。 */
final class CourseFormDialogSupport {
    private CourseFormDialogSupport() { }

    static void showScrollableForm(JDialog dialog, JPanel content, int width, int height,
            int minimumWidth, int minimumHeight) {
        JScrollPane scrollPane = VCampusTheme.scrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(VCampusTheme.PANEL);
        dialog.setContentPane(scrollPane);
        dialog.pack();
        dialog.setMinimumSize(UiMetrics.dimension(minimumWidth, minimumHeight));
        dialog.setSize(UiMetrics.dimension(width, height));
        dialog.setResizable(true);
        dialog.setLocationRelativeTo(dialog.getOwner());
    }

    static void styleField(JComponent component) {
        styleField(component, 320);
    }

    static void styleField(JComponent component, int minimumWidth) {
        component.setMinimumSize(UiMetrics.dimension(minimumWidth, 38));
        component.setPreferredSize(UiMetrics.dimension(minimumWidth, 38));
        VCampusTheme.roundedField(component);
    }

    static String courseStatusText(CourseStatus status) {
        return status == null ? "" : status.getDisplayName();
    }

    static String roundTypeText(SelectionRoundType type) {
        return type == null ? "" : type.getDisplayName();
    }

    static String courseCategoryText(SelectionType type) {
        return type == null ? "" : type.getDisplayName();
    }

    static DefaultListCellRenderer courseStatusRenderer() {
        return displayNameRenderer(value -> value instanceof CourseStatus
                ? courseStatusText((CourseStatus) value) : String.valueOf(value));
    }

    static DefaultListCellRenderer roundTypeRenderer() {
        return displayNameRenderer(value -> value instanceof SelectionRoundType
                ? roundTypeText((SelectionRoundType) value) : String.valueOf(value));
    }

    static DefaultListCellRenderer courseCategoryRenderer() {
        return displayNameRenderer(value -> value instanceof SelectionType
                ? courseCategoryText((SelectionType) value) : String.valueOf(value));
    }

    private static DefaultListCellRenderer displayNameRenderer(final TextProvider provider) {
        return new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focused) {
                return super.getListCellRendererComponent(list, provider.text(value), index, selected,
                        focused);
            }
        };
    }

    private interface TextProvider {
        String text(Object value);
    }
}
