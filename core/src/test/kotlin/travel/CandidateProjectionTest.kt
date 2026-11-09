package travel
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertNull
class CandidateProjectionTest {
    @TestFactory fun pythonCandidates(): List<DynamicTest> {
        val text = requireNotNull(javaClass.classLoader.getResourceAsStream("candidate-projection-cases.json")).bufferedReader().readText()
        val loader = SnapshotLoader()
        return Json.parseToJsonElement(text).jsonArray.map { entry ->
            val row = entry.jsonObject
            val city = row["city"]!!.jsonPrimitive.content
            val category = row["category"]!!.jsonPrimitive.content
            DynamicTest.dynamicTest("$city / $category") {
                val actual = requireNotNull(loader.candidates(city, category))
                assertNull(GoldenComparator.difference(row["options"]!!, actual, "$.candidates"))
            }
        }
    }
}
