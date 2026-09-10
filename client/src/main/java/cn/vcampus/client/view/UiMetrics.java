package cn.vcampus.client.view;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Toolkit;

/**
 * 客户端统一尺寸工具。
 *
 * <p>页面代码只记录以 96 DPI、100% 缩放为基准的逻辑尺寸；本类负责将其转换为当前设备上
 * 应使用的像素尺寸。后续新增或改造 Swing 页面时，应优先使用本类，而不是直接散落地编写
 * {@code new Dimension(...)}、固定字号或固定边距。</p>
 */
final class UiMetrics {
    static final int BASE_DPI = 96;
    private static final double MIN_SCALE = 0.75d;
    private static final double MAX_SCALE = 3.00d;
    private static final double MIN_DENSITY_SCALE = 0.90d;
    private static final double MAX_DENSITY_SCALE = 1.15d;
    private static final double DPI_ADJUSTMENT_WEIGHT = 0.20d;

    private UiMetrics() {
    }

    /** 根据当前主显示器的缩放信息计算 UI 比例；无图形环境时使用 100%。 */
    static double systemScale() {
        if (GraphicsEnvironment.isHeadless()) {
            return 1.0d;
        }
        try {
            GraphicsConfiguration configuration = GraphicsEnvironment
                    .getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .getDefaultConfiguration();
            double transformScale = Math.max(configuration.getDefaultTransform().getScaleX(),
                    configuration.getDefaultTransform().getScaleY());
            int dpi = Toolkit.getDefaultToolkit().getScreenResolution();
            // Swing 已按设备缩放绘制；这里只保留轻量密度调整，避免把 150% DPI 再完整放大一次。
            double platformScale = Math.max(transformScale, dpi / (double) BASE_DPI);
            return compactDensityScale(platformScale);
        } catch (RuntimeException unavailable) {
            // 远程桌面或部分图形驱动无法提供 DPI 信息时，保持基准尺寸而不是阻断客户端启动。
            return 1.0d;
        }
    }

    /** 将逻辑像素转换为当前设备使用的像素。 */
    static int px(int logicalPixels) {
        return px(logicalPixels, systemScale());
    }

    /** 供布局和测试在指定比例下转换尺寸。 */
    static int px(int logicalPixels, double scale) {
        if (logicalPixels < 0) {
            throw new IllegalArgumentException("logicalPixels must not be negative");
        }
        return (int) Math.round(logicalPixels * normalizeScale(scale));
    }

    static Dimension dimension(int logicalWidth, int logicalHeight) {
        return dimension(logicalWidth, logicalHeight, systemScale());
    }

    static Dimension dimension(int logicalWidth, int logicalHeight, double scale) {
        return new Dimension(px(logicalWidth, scale), px(logicalHeight, scale));
    }

    static Insets insets(int top, int left, int bottom, int right) {
        double scale = systemScale();
        return new Insets(px(top, scale), px(left, scale), px(bottom, scale), px(right, scale));
    }

    static Insets insets(int top, int left, int bottom, int right, double scale) {
        return new Insets(px(top, scale), px(left, scale), px(bottom, scale), px(right, scale));
    }

    static Font font(String family, int style, int logicalSize) {
        return font(family, style, logicalSize, systemScale());
    }

    static Font font(String family, int style, int logicalSize, double scale) {
        if (logicalSize <= 0) {
            throw new IllegalArgumentException("logicalSize must be positive");
        }
        return new Font(family, style, px(logicalSize, scale));
    }

    /** 将系统报告的 DPI 换算为基准 DPI 下的缩放比例。 */
    static double scaleForDpi(int dpi) {
        if (dpi <= 0) {
            return 1.0d;
        }
        return compactDensityScale(dpi / (double) BASE_DPI);
    }

    private static double compactDensityScale(double platformScale) {
        double adjusted = 1.0d + (normalizeScale(platformScale) - 1.0d)
                * DPI_ADJUSTMENT_WEIGHT;
        return Math.max(MIN_DENSITY_SCALE, Math.min(MAX_DENSITY_SCALE, adjusted));
    }

    private static double normalizeScale(double scale) {
        if (Double.isNaN(scale) || Double.isInfinite(scale) || scale <= 0.0d) {
            return 1.0d;
        }
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }
}
