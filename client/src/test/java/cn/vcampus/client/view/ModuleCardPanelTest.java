package cn.vcampus.client.view;

import java.awt.BorderLayout;
import javax.swing.JTextArea;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleCardPanelTest {
    @Test
    void summaryTextWrapsWithCardWidth() {
        ModuleDescriptor descriptor = new ModuleDescriptor(
                "选课系统", "查询课程、提交选课、退课并查看已选课程。", "待接入");

        ModuleCardPanel panel = new ModuleCardPanel(descriptor, event -> { });

        JTextArea summary = (JTextArea) ((BorderLayout) panel.getLayout())
                .getLayoutComponent(BorderLayout.CENTER);
        assertTrue(summary.getLineWrap());
        assertTrue(summary.getWrapStyleWord());
        assertFalse(summary.isEditable());
    }
}
