package travel

import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GoldenTest {
    private val json = Json { encodeDefaults = false; explicitNulls = true }
    private val root = File("src/test/resources/golden")
    @TestFactory fun fixtures(): List<DynamicTest> = listOf("e2e", "requirements").flatMap { folder ->
        File(root, folder).listFiles()!!.filter { it.extension == "json" && it.name != "dataset-gold.json" && (System.getProperty("golden.fixture", "").isEmpty() || it.nameWithoutExtension == System.getProperty("golden.fixture")) }.sortedBy { it.name }.map { file ->
            DynamicTest.dynamicTest("$folder/${file.name}") {
                val original = json.parseToJsonElement(file.readText(Charsets.UTF_8))
                val roundtrip = if (folder == "e2e") json.parseToJsonElement(json.encodeToString(json.decodeFromJsonElement<PlanResult>(original)))
                    else json.parseToJsonElement(json.encodeToString(json.decodeFromJsonElement<RequirementsFixture>(original)))
                assertNull(GoldenComparator.difference(original, roundtrip))
            }
        }
    }
    @Test fun snapshotCounts() {
        val loader = SnapshotLoader()
        assertEquals(12, loader.cityCount())
        assertEquals(114, loader.snapshotCandidateCount())
        assertEquals(150, loader.mockCandidateCount())
        assertEquals("Boston", loader.resolveCity("Boston"))
    }
}


