package cn.vcampus.client.view;

import java.awt.Dimension;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponsiveCardRowPanelTest {
    @Test
    void laysCardsSideBySideWhenThereIsRoom() {
        ResponsiveCardRowPanel row = new ResponsiveCardRowPanel(260, 18);
        JPanel first = fixedPanel(260, 120);
        JPanel second = fixedPanel(260, 140);
        row.add(first);
        row.add(second);

        row.setBounds(0, 0, 700, 300);
        row.doLayout();

        assertEquals(first.getY(), second.getY());
        assertTrue(second.getX() > first.getX());
        assertTrue(first.getWidth() >= 260);
        assertTrue(second.getWidth() >= 260);
    }

    @Test
    void stacksCardsWhenWindowIsCompact() {
        ResponsiveCardRowPanel row = new ResponsiveCardRowPanel(260, 18);
        JPanel first = fixedPanel(260, 120);
        JPanel second = fixedPanel(260, 140);
        row.add(first);
        row.add(second);

        row.setBounds(0, 0, 420, 400);
        row.doLayout();

        assertEquals(0, first.getX());
        assertEquals(0, second.getX());
        assertTrue(second.getY() > first.getY());
        assertEquals(row.getWidth(), first.getWidth());
        assertEquals(row.getWidth(), second.getWidth());
    }

    private static JPanel fixedPanel(int width, int height) {
        JPanel panel = new JPanel();
        panel.setPreferredSize(new Dimension(width, height));
        return panel;
    }
}
