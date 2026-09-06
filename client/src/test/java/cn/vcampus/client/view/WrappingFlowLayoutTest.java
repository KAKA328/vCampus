package cn.vcampus.client.view;

import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WrappingFlowLayoutTest {
    @Test
    void preferredHeightGrowsWhenControlsWrapOnCompactWidth() {
        JPanel panel = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, 10, 4));
        for (int index = 0; index < 6; index++) {
            JButton button = new JButton("按钮" + index);
            button.setPreferredSize(new Dimension(120, 36));
            panel.add(button);
        }

        panel.setSize(280, 400);
        Dimension preferred = panel.getPreferredSize();

        assertTrue(preferred.height >= 120,
                "wrapped toolbars should reserve enough height for multiple rows");
    }
}
