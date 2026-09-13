package cn.vcampus.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.course.CourseStatus;
import cn.vcampus.course.SelectionRoundType;
import cn.vcampus.course.SelectionType;
import java.awt.Component;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

/** 验证管理表单不暴露内部枚举值，且关键输入控件保留可读尺寸。 */
class CourseFormDialogSupportTest {
    @Test
    void presentsCourseStatusRoundTypeAndCourseCategoryInChinese() {
        assertEquals("启用", CourseFormDialogSupport.courseStatusText(CourseStatus.ACTIVE));
        assertEquals("重修轮次", CourseFormDialogSupport.roundTypeText(SelectionRoundType.RETAKE));
        assertEquals("必修", CourseFormDialogSupport.courseCategoryText(SelectionType.REQUIRED));
        assertEquals("停用", rendererText(CourseFormDialogSupport.courseStatusRenderer(),
                CourseStatus.DISABLED));
        assertEquals("首修轮次", rendererText(CourseFormDialogSupport.roundTypeRenderer(),
                SelectionRoundType.INITIAL));
        assertEquals("重修", rendererText(CourseFormDialogSupport.shortRoundTypeRenderer(),
                SelectionRoundType.RETAKE));
        assertEquals("选修", rendererText(CourseFormDialogSupport.courseCategoryRenderer(),
                SelectionType.ELECTIVE));
    }

    @Test
    void keepsPrimaryAndCompactFormInputsReadable() {
        JTextField primary = new JTextField();
        CourseFormDialogSupport.styleField(primary);
        assertTrue(primary.getMinimumSize().width >= UiMetrics.px(320));
        assertTrue(primary.getMinimumSize().height >= UiMetrics.px(38));

        JComboBox<String> compact = new JComboBox<String>();
        CourseFormDialogSupport.styleField(compact, 80);
        assertTrue(compact.getMinimumSize().width >= UiMetrics.px(80));
        assertTrue(compact.getMinimumSize().height >= UiMetrics.px(38));

        JLabel label = CourseFormDialogSupport.fieldLabel("轮次编号");
        assertTrue(label.getMinimumSize().width >= UiMetrics.px(120));
        assertTrue(label.getMinimumSize().height >= UiMetrics.px(38));
    }

    private static String rendererText(javax.swing.ListCellRenderer<Object> renderer,
            Object value) {
        Component component = renderer.getListCellRendererComponent(new JList<Object>(), value,
                0, false, false);
        return ((JLabel) component).getText();
    }
}
