package travel

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFormattingTest {
    @Test fun normalizesRoundedNegativeZero() {
        assertEquals("0.00", MoneyFormatting.format(-0.0))
        assertEquals("0.00", MoneyFormatting.format(BigDecimal("-0.004")))
        assertEquals("-0.01", MoneyFormatting.format(BigDecimal("-0.006")))
        assertEquals("2.67", MoneyFormatting.format(2.675))
    }
}
