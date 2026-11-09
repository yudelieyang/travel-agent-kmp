package travel

import java.math.BigDecimal
import java.math.RoundingMode

/** D7 UI money display: rounded negative zero is shown without a minus sign. */
object MoneyFormatting {
    fun format(value: BigDecimal): String {
        val rounded = value.setScale(2, RoundingMode.HALF_EVEN)
        return if (rounded.signum() == 0) "0.00" else rounded.toPlainString()
    }
    // D2 expressly permits BigDecimal(Double) for UI display of the exact binary value.
    fun format(value: Double): String = format(BigDecimal(value))
}
