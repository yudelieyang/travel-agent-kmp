package travel
import java.io.File
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertNull
class FullPipelineParityTest {
    private val engine = TravelEngine()
    private val json = Json { explicitNulls = true; encodeDefaults = false }
    @TestFactory fun fixtures(): List<DynamicTest> = File("src/test/resources/golden/e2e").listFiles()!!.filter { it.extension == "json" }.sortedBy { it.name }.map { file ->
        DynamicTest.dynamicTest(file.name) {
            val expected = json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
            val actual = engine.run(expected["query"]!!.jsonPrimitive.content)
            assertNull(GoldenComparator.difference(expected, JsonObject(json.encodeToJsonElement(PlanResult.serializer(), actual.result).jsonObject - "errors")))
        }
    }
}
