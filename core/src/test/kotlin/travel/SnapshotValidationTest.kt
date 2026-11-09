package travel
import kotlinx.serialization.json.*
import kotlin.test.*
class SnapshotValidationTest {
    private fun source(path: String): String = requireNotNull(javaClass.classLoader.getResourceAsStream(path)).bufferedReader().use { it.readText() }
    private val manifestPath = "data/travel/snapshots/openstreetmap/migration_manifest.json"
    @Test fun rejectsDuplicateManifestRoute() {
        val original = Json.parseToJsonElement(source(manifestPath)).jsonObject
        val routes = original["routes"]!!.jsonArray
        val modified = JsonObject(original + ("routes" to JsonArray(routes + routes.first())))
        val error = assertFailsWith<CandidateProviderException> { SnapshotLoader { if (it == manifestPath) modified.toString() else source(it) } }
        assertEquals("Candidate migration manifest unavailable or invalid", error.message)
    }
    @Test fun rejectsSnapshotRecordCount() {
        val loader = Json.parseToJsonElement(source(manifestPath)).jsonObject
        val file = "data/travel/snapshots/openstreetmap/" + loader["routes"]!!.jsonArray.first().jsonObject["snapshot"]!!.jsonPrimitive.content
        val snapshot = Json.parseToJsonElement(source(file)).jsonObject
        val modified = JsonObject(snapshot + ("record_count" to JsonPrimitive(9999)))
        val error = assertFailsWith<CandidateProviderException> { SnapshotLoader { if (it == file) modified.toString() else source(it) } }
        assertEquals("Candidate snapshot unavailable or invalid", error.message)
    }
}
