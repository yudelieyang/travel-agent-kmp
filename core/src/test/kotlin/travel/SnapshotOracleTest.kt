package travel
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
class SnapshotOracleTest {
    @TestFactory fun pythonValidationCases(): List<DynamicTest> {
        val json = Json.parseToJsonElement(requireNotNull(javaClass.classLoader.getResourceAsStream("validation-cases.json")).bufferedReader().readText()).jsonArray
        return json.map { entry ->
            val test = entry.jsonObject
            DynamicTest.dynamicTest(test["id"]!!.jsonPrimitive.content) {
                val result = runCatching { SnapshotValidation.snapshot(test["snapshot"]!!.jsonObject) { name -> if (name == "Boston") "Boston" to "MA" else null } }.isSuccess
                assertEquals(test["accepted"]!!.jsonPrimitive.boolean, result)
            }
        }
    }
}
