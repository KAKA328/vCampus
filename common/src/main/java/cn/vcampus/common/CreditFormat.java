package cn.vcampus.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 学分统一使用两位以内的小数，并以不带无意义尾零的文本展示。 */
public final class CreditFormat {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private CreditFormat() { }

    public static BigDecimal positive(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(ZERO) <= 0 || value.scale() > 2) {
            throw new IllegalArgumentException(fieldName + " must be a positive decimal with at most 2 places");
        }
        return value.stripTrailingZeros();
    }

    public static BigDecimal nonNegative(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(ZERO) < 0 || value.scale() > 2) {
            throw new IllegalArgumentException(fieldName + " must be a non-negative decimal with at most 2 places");
        }
        return value.stripTrailingZeros();
    }

    public static String display(BigDecimal value) {
        if (value == null) return "—";
        return value.setScale(Math.max(0, value.stripTrailingZeros().scale()), RoundingMode.UNNECESSARY)
                .toPlainString();
    }
}
