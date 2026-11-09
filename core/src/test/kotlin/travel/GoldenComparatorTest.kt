package travel

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GoldenComparatorTest {
    private val json = Json
    @Test fun monetaryFieldsUseTheRightPrecision() {
        fun compare(path: String, a: String, b: String) = GoldenComparator.difference(json.parseToJsonElement(a), json.parseToJsonElement(b), path)
        assertNull(compare("$.budget.estimated_total_cost", "\"1.005\"", "\"1.00\""))
        assertNotNull(compare("$.candidates[0].price", "\"1.005\"", "\"1.00\""))
        assertNotNull(compare("$.budget.provided_limit", "null", "\"0\""))
        assertNotNull(compare("$.budget.provided_limit", "\"1.00\"", "\"2.00\""))
    }
}
