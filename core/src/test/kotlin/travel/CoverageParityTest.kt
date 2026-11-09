package travel
import java.io.File
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertNull
class CoverageParityTest {
    private val parser = RequirementsParser()
    private val analyzer = SemanticCoverageAnalyzer()
    private val json = Json
    @TestFactory fun fixtures(): List<DynamicTest> = File("src/test/resources/golden/requirements").listFiles()!!.filter { it.name.startsWith("SEM200-") && it.extension == "json" }.sortedBy { it.name }.map { file ->
        DynamicTest.dynamicTest(file.name) {
            val record = json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
            val query = record["query"]!!.jsonPrimitive.content
            val actual = json.encodeToJsonElement(CoverageResult.serializer(), analyzer.analyze(query, parser.parse(query)))
            assertNull(GoldenComparator.difference(record["coverage"]!!, actual))
        }
    }
}
