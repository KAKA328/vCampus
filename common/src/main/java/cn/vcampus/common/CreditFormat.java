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

    /**
     * Compatibility value for legacy integer wire fields.
     *
     * <p>Fractional precision is available only through the parallel decimal field. The legacy
     * value is rounded toward zero so an old client never overstates earned or required credits.</p>
     */
    public static int legacyInt(BigDecimal value, String fieldName) {
        BigDecimal normalized = nonNegative(value, fieldName);
        try {
            return normalized.setScale(0, RoundingMode.DOWN).intValueExact();
        } catch (ArithmeticException outOfRange) {
            throw new IllegalArgumentException(fieldName + " exceeds legacy integer range", outOfRange);
        }
    }

    /** Uses the precise field when present, otherwise upgrades a legacy serialized integer. */
    public static BigDecimal decimalOrLegacy(BigDecimal precise, int legacy) {
        return precise == null ? BigDecimal.valueOf(legacy) : precise;
    }
}
