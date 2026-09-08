package cn.vcampus.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

/** 验证选课页面复用的状态提示会保留完整消息。 */
class CourseUiSupportTest {
    @Test
    void statusFeedbackKeepsMessageAndTooltipTogether() {
        JLabel label = new JLabel();

        CourseUiSupport.showStatus(label, "还有 2 名学生未录入成绩，不能提交审核", VCampusTheme.DANGER);

        assertEquals("还有 2 名学生未录入成绩，不能提交审核", label.getText());
        assertEquals(label.getText(), label.getToolTipText());
        assertTrue(label.isOpaque());
    }
}
