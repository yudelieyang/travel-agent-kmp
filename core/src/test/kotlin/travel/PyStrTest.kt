package travel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.*
class PyStrTest {
    @Test fun pythonMappings() {
        assertEquals("Boston", SnapshotLoader().resolveCity("Boſton"))
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream("unicode-cases.json"))
        val cases = Json.parseToJsonElement(stream.bufferedReader().readText()).jsonArray
        cases.forEach { item ->
            val row = item.jsonObject
            val input = row["input"]!!.jsonPrimitive.content
            assertEquals(row["lower"]!!.jsonPrimitive.content, PyStr.lower(input), "lower: $input")
            assertEquals(row["casefold"]!!.jsonPrimitive.content, PyStr.casefold(input), "casefold: $input")
        }
    }
}
