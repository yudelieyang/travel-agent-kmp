package travel

import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.serialization.json.*

/** Fixture comparison; path identifies money fields and preserves object/list structure. */
object GoldenComparator {
    private val money = setOf("price", "budget_amount", "value", "provided_limit", "estimated_cost", "estimated_total_cost", "per_traveler_cost", "comparison_cost", "remaining_budget", "actual", "expected", "excess")
    private val unrounded = setOf("price", "budget_amount", "value", "provided_limit")
    fun difference(expected: JsonElement, actual: JsonElement, path: String = "$"): String? {
        if (expected is JsonObject && actual is JsonObject) {
            if (expected.keys != actual.keys) return "$path: keys expected ${expected.keys}, actual ${actual.keys}"
            for (key in expected.keys) difference(expected.getValue(key), actual.getValue(key), "$path.$key")?.let { return it }
            return null
        }
        if (expected is JsonArray && actual is JsonArray) {
            if (expected.size != actual.size) return "$path: length expected ${expected.size}, actual ${actual.size}"
            for (i in expected.indices) difference(expected[i], actual[i], "$path[$i]")?.let { return it }
            return null
        }
        if (expected is JsonNull || actual is JsonNull) return if (expected == actual) null else "$path: expected $expected, actual $actual"
        val key = path.substringAfterLast('.')
        if ((key in money || path.contains(".breakdown.")) && expected is JsonPrimitive && actual is JsonPrimitive) {
            val a = expected.content.toBigDecimalOrNull()
            val b = actual.content.toBigDecimalOrNull()
            if (a != null && b != null) {
                val result = if (key in unrounded) a.compareTo(b) else a.setScale(2, RoundingMode.HALF_EVEN).compareTo(b.setScale(2, RoundingMode.HALF_EVEN))
                return if (result == 0) null else "$path: expected $expected, actual $actual"
            }
        }
        return if (expected == actual) null else "$path: expected $expected, actual $actual"
    }
}
