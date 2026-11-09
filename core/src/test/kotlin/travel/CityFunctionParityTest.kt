package travel
import java.io.File
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertNull

class CityFunctionParityTest {
    @TestFactory fun cases(): List<DynamicTest> {
        val parser = CityParser()
        val records = Json.parseToJsonElement(File("src/test/resources/parity/functions/python.json").readText(Charsets.UTF_8)).jsonObject["cases"]!!.jsonArray
        return records.map { item ->
            val row = item.jsonObject
            DynamicTest.dynamicTest(row["id"]!!.jsonPrimitive.content) {
                val query = row["query"]!!.jsonPrimitive.content
                val start = parser.cityAtStart(query)
                val actualStart = if (start == null) JsonNull else JsonArray(listOf(start.first, JsonPrimitive(start.second)))
                assertNull(GoldenComparator.difference(row["city_at_start"]!!, actualStart, "$.city_at_start"))
                assertNull(GoldenComparator.difference(row["resolve_city"]!!, parser.resolveCity(query) ?: JsonNull, "$.resolve_city"))
            }
        }
    }
}
