package cn.vcampus.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CreditFormatTest {
    @Test
    void displaysWholeAndFractionalCreditsWithoutTrailingZeros() {
        assertEquals("3", CreditFormat.display(new BigDecimal("3.00")));
        assertEquals("1.5", CreditFormat.display(new BigDecimal("1.50")));
    }
}
