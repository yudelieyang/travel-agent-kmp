package travel

import java.math.BigDecimal
import java.math.RoundingMode

// Port of planner.py:hotel_price_ceiling (L126–139).
fun pyFloatHotelCeiling(value: BigDecimal, travelers: Int, nights: Int): Double {
    val denominator = travelers.toLong() * nights
    if (denominator == 0L) throw ArithmeticException("float division by zero")
    return value.toDouble() / denominator.toDouble()
}

// Port of candidate_data.py:candidates_for (L146,151) and itinerary.py:select_option (L121–122).
fun pyFloatCompare(a: BigDecimal, b: BigDecimal): Int = a.toDouble().compareTo(b.toDouble())
fun pyFloatExceeds(price: BigDecimal, ceiling: Double): Boolean = price.toDouble() > ceiling

// Port of itinerary.py:validate_itinerary (L138–162).
fun pyFloatEqual(a: BigDecimal, b: BigDecimal): Boolean = a.toDouble() == b.toDouble()

// Port of service.py:TravelService.plan (L193–203): BigDecimal(Double) is intentional here only.
fun pyFloatSavings(initial: BigDecimal, final: BigDecimal): Double {
    val difference = initial.toDouble() - final.toDouble()
    val rounded = BigDecimal(difference).setScale(2, RoundingMode.HALF_EVEN).toDouble()
    return if (rounded < 0.0) 0.0 else rounded
}

fun pyFloatPositive(value: BigDecimal): Boolean = value.toDouble() > 0.0

// D1/D7: IEEE 754 numeric equality, with NaN always rejected and signed zeros equal.
fun pyFloatIeeeEqual(a: Double, b: Double): Boolean = !a.isNaN() && !b.isNaN() && a == b

// P2 coverage numeric parsing uses the same binary64 conversions as semantic_coverage.py.
fun pyFloatParseAmount(text: String): Double = text.toDouble()
fun pyFloatFromDecimal(value: BigDecimal): Double = value.toDouble()
