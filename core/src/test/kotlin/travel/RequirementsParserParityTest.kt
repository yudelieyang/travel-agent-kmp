package travel
import java.io.File
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertNull
class RequirementsParserParityTest {
    private val parser = RequirementsParser()
    private val json = Json { explicitNulls = true; encodeDefaults = false }
    @TestFactory fun fixtures(): List<DynamicTest> = File("src/test/resources/golden/requirements").listFiles()!!.filter { it.name.startsWith("SEM200-") && it.extension == "json" }.sortedBy { it.name }.map { file ->
        DynamicTest.dynamicTest(file.name) {
            val expected = json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
            val actual = json.encodeToJsonElement(TravelRequirements.serializer(), parser.parse(expected["query"]!!.jsonPrimitive.content))
            assertNull(GoldenComparator.difference(expected["requirements"]!!, actual))
        }
    }
}
