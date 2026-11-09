package travel
import java.io.File
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
class P2GoldExportTest {
    @Test fun writeKotlinResults() {
        val json = Json { explicitNulls = true; encodeDefaults = false }
        val parser = RequirementsParser(); val coverage = SemanticCoverageAnalyzer()
        val files = File("src/test/resources/golden/requirements").listFiles()!!.filter { it.name.startsWith("SEM200-") && it.extension == "json" }.sortedBy { it.name }
        assertEquals(200, files.size)
        val data = files.map { file ->
            val record = json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
            val query = record["query"]!!.jsonPrimitive.content
            val req = parser.parse(query)
            buildJsonObject { put("id", record["id"]!!); put("requirements", json.encodeToJsonElement(TravelRequirements.serializer(), req)); put("coverage", json.encodeToJsonElement(CoverageResult.serializer(), coverage.analyze(query, req))) }
        }
        File("build/p2-kotlin-results.json").apply { parentFile.mkdirs(); writeText(JsonArray(data).toString(), Charsets.UTF_8) }
    }
}
