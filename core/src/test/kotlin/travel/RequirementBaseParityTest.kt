package travel
import java.io.File
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import kotlin.test.assertNull
class RequirementBaseParityTest {
    private val json = Json { explicitNulls = true; encodeDefaults = false }
    private val functions = RequirementFunctions()
    private val records = json.parseToJsonElement(File("src/test/resources/parity/functions/python.json").readText(Charsets.UTF_8)).jsonObject["cases"]!!.jsonArray
    @TestFactory fun recognizedMoneyExpressions(): List<DynamicTest> = records.map { entry ->
        val row = entry.jsonObject; DynamicTest.dynamicTest(row["id"]!!.jsonPrimitive.content) {
            val actual = JsonArray(functions.recognizedMoneyExpressions(row["query"]!!.jsonPrimitive.content).map { match ->
                buildJsonObject {
                    put("text", match.groups[0].value!!)
                    put("span", JsonArray(match.groups[0].span.map(::JsonPrimitive)))
                    put("groups", JsonObject(match.named.mapValues { it.value.value?.let(::JsonPrimitive) ?: JsonNull }))
                    put("group_spans", JsonObject(match.named.mapValues { JsonArray(it.value.span.map(::JsonPrimitive)) }))
                }
            })
            assertEquals(row["recognized_money_expressions"], actual)
        }
    }
    @TestFactory fun budgetStrength(): List<DynamicTest> = records.map { entry ->
        val row = entry.jsonObject; DynamicTest.dynamicTest(row["id"]!!.jsonPrimitive.content) {
            assertEquals(row["budget_strength"]!!.jsonPrimitive.content, functions.budgetStrength(row["query"]!!.jsonPrimitive.content))
        }
    }
    @TestFactory fun monetaryScope(): List<DynamicTest> = records.map { entry ->
        val row = entry.jsonObject; DynamicTest.dynamicTest(row["id"]!!.jsonPrimitive.content) {
            for (context in row["amount_context"]!!.jsonArray) {
                val c = context.jsonObject
                val expected = c["monetary_scope"].let { if (it == null || it is JsonNull) null else it.jsonPrimitive.content }
                assertEquals(expected, functions.monetaryScope(c["prefix"]!!.jsonPrimitive.content, c["suffix"]!!.jsonPrimitive.content, c["intro"]!!.jsonPrimitive.content))
            }
        }
    }
    @TestFactory fun budgetCurrency(): List<DynamicTest> = records.map { entry ->
        val row = entry.jsonObject; DynamicTest.dynamicTest(row["id"]!!.jsonPrimitive.content) {
            val matches = SourcePatterns.at("agent.requirements", 403).finditer(row["query"]!!.jsonPrimitive.content)
            for (context in row["amount_context"]!!.jsonArray) {
                val c = context.jsonObject; val span = c["match"]!!.jsonObject["span"]!!.jsonArray.map { it.jsonPrimitive.int }
                val match = matches.single { it.groups[0].span == span }
                assertEquals(c["budget_currency"]!!.jsonPrimitive.content, functions.budgetCurrency(match))
            }
        }
    }
    @TestFactory fun mergeBudgetConstraints(): List<DynamicTest> = records.map { entry ->
        val row = entry.jsonObject; DynamicTest.dynamicTest(row["id"]!!.jsonPrimitive.content) {
            val extraction = row["extract_budget_constraints"]!!.jsonObject
            if (extraction["error"] is JsonNull) {
                val expected = row["merge_budget_constraints"]!!.jsonObject["value"]!!
                val items = extraction["value"]!!.jsonArray[0].jsonArray.map { json.decodeFromJsonElement<BudgetConstraint>(it) }
                val actual = JsonArray(functions.mergeBudgetConstraints(items).map { json.encodeToJsonElement(BudgetConstraint.serializer(), it) })
                assertNull(GoldenComparator.difference(expected, actual))
            }
        }
    }
}
