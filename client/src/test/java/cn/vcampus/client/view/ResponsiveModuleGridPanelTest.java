package cn.vcampus.client.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponsiveModuleGridPanelTest {
    @Test
    void columnCountAdaptsToAvailableWidth() {
        assertEquals(1, ResponsiveModuleGridPanel.columnsForWidth(360, 300, 16));
        assertEquals(2, ResponsiveModuleGridPanel.columnsForWidth(760, 300, 16));
        assertEquals(3, ResponsiveModuleGridPanel.columnsForWidth(1040, 300, 16));
    }

    @Test
    void tracksViewportWidthSoCardsStretchWithWindow() {
        ResponsiveModuleGridPanel panel = new ResponsiveModuleGridPanel();

        assertTrue(panel.getScrollableTracksViewportWidth());
        assertEquals(16, panel.getHgap());
        assertEquals(16, panel.getVgap());
    }
}
