package travel

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.assertEquals

class InputPreviewParityTest {
    @Test fun requirementsAndCoverageMatchEngine() {
        val fixtures = File("../core/src/test/resources/golden/requirements").listFiles()!!.filter { it.name.startsWith("SEM200-") }.sortedBy { it.name }.take(30)
        assertEquals(30, fixtures.size)
        val engine = TravelEngine()
        fixtures.forEach { file ->
            val query = Json.parseToJsonElement(file.readText()).jsonObject.getValue("query").jsonPrimitive.content
            val preview = parseInputPreview(query)
            val run = engine.run(query)
            assertEquals(run.result.requirements, preview.requirements, file.name)
            assertEquals(run.coverage, preview.coverage, file.name)
        }
    }
}
