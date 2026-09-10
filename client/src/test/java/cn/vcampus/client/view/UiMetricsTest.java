package cn.vcampus.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import org.junit.jupiter.api.Test;

/** 验证统一尺寸换算，避免不同页面各自实现不一致的缩放规则。 */
class UiMetricsTest {
    @Test
    void dpiScalingAddsOnlyACompactDensityAdjustment() {
        assertEquals(1.0d, UiMetrics.scaleForDpi(96), 0.0001d);
        assertEquals(1.05d, UiMetrics.scaleForDpi(120), 0.0001d);
        assertEquals(1.10d, UiMetrics.scaleForDpi(144), 0.0001d);
        assertEquals(1.15d, UiMetrics.scaleForDpi(192), 0.0001d);
        assertEquals(1.15d, UiMetrics.scaleForDpi(288), 0.0001d);
    }

    @Test
    void dimensionsInsetsAndFontsUseTheSameScale() {
        assertEquals(50, UiMetrics.px(40, 1.25d));
        assertEquals(new Dimension(283, 55), UiMetrics.dimension(226, 44, 1.25d));
        assertEquals(new Insets(20, 25, 20, 25), UiMetrics.insets(16, 20, 16, 20, 1.25d));

        Font font = UiMetrics.font("Microsoft YaHei UI", Font.BOLD, 24, 1.25d);
        assertEquals(30, font.getSize());
        assertEquals(Font.BOLD, font.getStyle());
    }

    @Test
    void invalidLogicalSizesAreRejectedAndSystemScaleRemainsUsable() {
        assertThrows(IllegalArgumentException.class, () -> UiMetrics.px(-1, 1.0d));
        assertThrows(IllegalArgumentException.class,
                () -> UiMetrics.font("Microsoft YaHei UI", Font.PLAIN, 0, 1.0d));
        assertTrue(UiMetrics.systemScale() >= 0.90d);
        assertTrue(UiMetrics.systemScale() <= 1.15d);
    }
}
