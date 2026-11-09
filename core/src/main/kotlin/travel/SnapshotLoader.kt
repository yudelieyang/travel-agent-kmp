package travel

import kotlinx.serialization.json.*

class CandidateProviderException(message: String) : IllegalArgumentException(message)

/** Port of candidate_providers.py:RealSnapshotProvider, MixedCandidateProvider and create_candidate_provider. */
class SnapshotLoader(private val read: (String) -> String = { path ->
    requireNotNull(SnapshotLoader::class.java.classLoader.getResourceAsStream(path)) { "Missing $path" }
        .bufferedReader(Charsets.UTF_8).use { it.readText() }
}) {
    private val root = "data/travel/snapshots/openstreetmap/"
    private val cities = Json.parseToJsonElement(read("data/travel/us/cities.json")).jsonObject["cities"]!!.jsonArray
    private val routes: List<JsonObject>
    private val snapshots: Map<String, JsonObject>
    init {
        try {
            val manifest = Json.parseToJsonElement(read(root + "migration_manifest.json")).jsonObject
            routes = SnapshotValidation.manifest(manifest)
            snapshots = routes.map { it["snapshot"]!!.jsonPrimitive.content.trim() }.distinct().associateWith { path ->
                val snapshot = try {
                    Json.parseToJsonElement(read(root + path)).jsonObject.also { data ->
                        SnapshotValidation.snapshot(data) { name ->
                            val canonical = resolveCity(name)
                            cities.firstOrNull { it.jsonObject["city"]!!.jsonPrimitive.content == canonical }?.jsonObject?.let {
                                it["city"]!!.jsonPrimitive.content to it["state"]!!.jsonPrimitive.content
                            }
                        }
                    }
                } catch (e: Exception) { throw CandidateProviderException("Candidate snapshot unavailable or invalid") }
                snapshot
            }
            for (route in routes) {
                val city = route["city"]!!.jsonPrimitive.content.trim()
                val cat = route["category"]!!.jsonPrimitive.content.trim()
                val snap = snapshots.getValue(route["snapshot"]!!.jsonPrimitive.content.trim())
                val canonical = resolveCity(city)
                val cityNode = snap["cities"]!!.jsonArray.firstOrNull { it.jsonObject["city"]!!.jsonPrimitive.content.trim() == canonical }
                val rows = cityNode?.jsonObject?.get("candidates")?.jsonObject?.get(cat)?.jsonArray
                if (rows.isNullOrEmpty()) throw CandidateProviderException("Candidate snapshot missing a declared migrated route")
            }
        } catch (e: CandidateProviderException) { throw e
        } catch (e: Exception) { throw CandidateProviderException("Candidate migration manifest unavailable or invalid") }
    }
    // Port of candidate_data.py:location_key and resolve_city (L70–90).
    private fun locationKey(value: String) = Regex("[a-z0-9]+").findAll(PyStr.casefold(value)).joinToString(" ") { it.value }
    fun resolveCity(value: String): String? = cities.firstOrNull { c ->
        val o = c.jsonObject
        (listOf(o["city"]!!.jsonPrimitive.content) + o["aliases"]!!.jsonArray.map { it.jsonPrimitive.content }).any { locationKey(it) == locationKey(value) }
    }?.jsonObject?.get("city")?.jsonPrimitive?.content
    fun candidates(city: String, category: String): JsonArray? {
        val resolved = resolveCity(city) ?: return null
        val route = routes.firstOrNull { r ->
            r["city"]!!.jsonPrimitive.content.trim() == resolved && r["category"]!!.jsonPrimitive.content.trim() == category
        } ?: return cities.first { it.jsonObject["city"]!!.jsonPrimitive.content == resolved }.jsonObject["candidates"]!!.jsonObject[category]?.jsonArray
        val snapshot = snapshots.getValue(route["snapshot"]!!.jsonPrimitive.content.trim())
        return snapshot["cities"]!!.jsonArray.firstOrNull { it.jsonObject["city"]!!.jsonPrimitive.content.trim() == resolved }?.jsonObject?.get("candidates")?.jsonObject?.get(category)?.jsonArray ?: JsonArray(emptyList())
    }
    fun cityCount() = cities.size
    fun snapshotCandidateCount(): Int = routes.sumOf { route -> candidates(route["city"]!!.jsonPrimitive.content, route["category"]!!.jsonPrimitive.content)!!.size }
    fun mockCandidateCount(): Int = cities.sumOf { city -> city.jsonObject["candidates"]!!.jsonObject.values.sumOf { it.jsonArray.size } }
}
