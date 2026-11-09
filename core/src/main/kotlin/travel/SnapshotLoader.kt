package travel

import kotlinx.serialization.json.*

/** Port of backend/app/tools/candidate_providers.py:RealSnapshotProvider and manifest routing. */
class SnapshotLoader(private val read: (String) -> String = { path ->
    requireNotNull(SnapshotLoader::class.java.classLoader.getResourceAsStream(path)) { "Missing $path" }
        .bufferedReader(Charsets.UTF_8).use { it.readText() }
}) {
    private val json = Json
    private val root = "data/travel/snapshots/openstreetmap/"
    private val cities = json.parseToJsonElement(read("data/travel/us/cities.json")).jsonObject["cities"]!!.jsonArray
    private val manifest = json.parseToJsonElement(read(root + "migration_manifest.json")).jsonObject
    private fun locationKey(value: String) = Regex("[a-z0-9]+").findAll(value.lowercase(java.util.Locale.ROOT)).joinToString(" ") { it.value }
    fun resolveCity(value: String): String? = cities.firstOrNull { c ->
        val o = c.jsonObject
        (listOf(o["city"]!!.jsonPrimitive.content) + o["aliases"]!!.jsonArray.map { it.jsonPrimitive.content }).any { locationKey(it) == locationKey(value) }
    }?.jsonObject?.get("city")?.jsonPrimitive?.content
    fun candidates(city: String, category: String): JsonArray? {
        val resolved = resolveCity(city) ?: return null
        val route = manifest["routes"]!!.jsonArray.firstOrNull { r ->
            r.jsonObject["city"]!!.jsonPrimitive.content == resolved && r.jsonObject["category"]!!.jsonPrimitive.content == category
        } ?: return cities.first { it.jsonObject["city"]!!.jsonPrimitive.content == resolved }.jsonObject["candidates"]!!.jsonObject[category]?.jsonArray
        val snapshot = json.parseToJsonElement(read(root + route.jsonObject["snapshot"]!!.jsonPrimitive.content)).jsonObject
        return snapshot["cities"]!!.jsonArray.first().jsonObject["candidates"]!!.jsonObject[category]!!.jsonArray
    }
    fun cityCount() = cities.size
    fun snapshotCandidateCount(): Int = manifest["routes"]!!.jsonArray.sumOf { route ->
        val o = route.jsonObject
        candidates(o["city"]!!.jsonPrimitive.content, o["category"]!!.jsonPrimitive.content)!!.size
    }
    fun mockCandidateCount(): Int = cities.sumOf { city -> city.jsonObject["candidates"]!!.jsonObject.values.sumOf { it.jsonArray.size } }
}
