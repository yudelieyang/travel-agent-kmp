package travel
import java.io.File
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals

class CoverageFunctionParityTest {
    private val json = Json
    private val functions = CoverageFunctions()
    private val records = json.parseToJsonElement(File("src/test/resources/parity/functions/python.json").readText(Charsets.UTF_8)).jsonObject["cases"]!!.jsonArray
    private fun expected(record: JsonObject, field: String) = record[field]!!.jsonObject["value"]!!
    private fun requirements(row: JsonObject): TravelRequirements? {
        val parsed = row["parsed"]!!.jsonObject["value"]!!
        return if (parsed is JsonNull) null else json.decodeFromJsonElement<TravelRequirements>(parsed)
    }
    @TestFactory fun amounts(): List<DynamicTest> = records.map { entry ->
        val row = entry.jsonObject; DynamicTest.dynamicTest(row["id"]!!.jsonPrimitive.content) {
            val actual = JsonArray(functions.budgetLikeAmounts(row["query"]!!.jsonPrimitive.content).map { item ->
                JsonArray(listOf(JsonPrimitive(item.value), JsonPrimitive(item.start), JsonPrimitive(item.end), JsonPrimitive(item.clause)))
            })
            assertEquals(expected(row, "budget_like_amounts"), actual)
        }
    }
    @TestFactory fun hardMoney(): List<DynamicTest> = records.map { entry ->
        val row = entry.jsonObject; DynamicTest.dynamicTest(row["id"]!!.jsonPrimitive.content) {
            val req = requirements(row)
            if (req != null) assertEquals(expected(row, "unrepresented_hard_money"), JsonArray(functions.unrepresentedHardMoney(row["query"]!!.jsonPrimitive.content, req).map(::JsonPrimitive)))
        }
    }
    @TestFactory fun correction(): List<DynamicTest> = records.map { entry ->
        val row = entry.jsonObject; DynamicTest.dynamicTest(row["id"]!!.jsonPrimitive.content) {
            val req = requirements(row)
            if (req != null) assertEquals(expected(row, "unresolved_correction"), functions.unresolvedCorrection(row["query"]!!.jsonPrimitive.content, req)?.let(::JsonPrimitive) ?: JsonNull)
        }
    }
    @TestFactory fun exclusion(): List<DynamicTest> = records.map { entry ->
        val row = entry.jsonObject; DynamicTest.dynamicTest(row["id"]!!.jsonPrimitive.content) {
            assertEquals(expected(row, "unrepresented_exclusion"), functions.unrepresentedExclusion(row["query"]!!.jsonPrimitive.content)?.let(::JsonPrimitive) ?: JsonNull)
        }
    }
}
