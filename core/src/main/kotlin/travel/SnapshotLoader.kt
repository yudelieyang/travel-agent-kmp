package travel

import kotlinx.serialization.json.*

class CandidateProviderException(message: String) : IllegalArgumentException(message)

/** Port of candidate_providers.py:RealSnapshotProvider, MixedCandidateProvider and create_candidate_provider. */
class SnapshotLoader(private val read: (String) -> String = { path ->
    requireNotNull(SnapshotLoader::class.java.classLoader.getResourceAsStream(path)) { "Missing $path" }
        .bufferedReader(Charsets.UTF_8).use { it.readText() }
}) {
    private val root = "data/travel/snapshots/openstreetmap/"
    private val cities = Json.parseToJsonElement(read("data/travel/us/cities.json")).jsonObject.also { SnapshotValidation.fixtureDataset(it) }["cities"]!!.jsonArray
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
    // Port of RealSnapshotProvider.candidates and fixture_candidates: project source facts to TravelOption.
    fun candidates(city: String, category: String): JsonArray? {
        val resolved = resolveCity(city) ?: return null
        val registered = cities.first { it.jsonObject["city"]!!.jsonPrimitive.content == resolved }.jsonObject
        val state = registered["state"]!!.jsonPrimitive.content
        val route = routes.firstOrNull { r ->
            r["city"]!!.jsonPrimitive.content.trim() == resolved && r["category"]!!.jsonPrimitive.content.trim() == category
        }
        val snapshot = route?.let { snapshots.getValue(it["snapshot"]!!.jsonPrimitive.content.trim()) }
        val rows = if (snapshot == null) registered["candidates"]!!.jsonObject[category]?.jsonArray
        else snapshot["cities"]!!.jsonArray.firstOrNull { it.jsonObject["city"]!!.jsonPrimitive.content.trim() == resolved }?.jsonObject?.get("candidates")?.jsonObject?.get(category)?.jsonArray ?: JsonArray(emptyList())
        return rows?.map { project(it.jsonObject, resolved, state, category, snapshot) }?.let(::JsonArray)
    }
    private fun project(row: JsonObject, city: String, state: String, category: String, snapshot: JsonObject?): JsonObject {
        val real = snapshot != null
        fun normalized(value: JsonElement): JsonElement = when (value) {
            is JsonObject -> JsonObject(value.mapValues { normalized(it.value) })
            is JsonArray -> JsonArray(value.map(::normalized))
            is JsonPrimitive -> if (real && value.isString) JsonPrimitive(value.content.trim()) else value
        }
        fun timestamp(value: JsonElement): JsonElement {
            val raw = value.jsonPrimitive.content
            val number = raw.toDoubleOrNull()
            val time = if (number != null) {
                val seconds = if (kotlin.math.abs(number) > 2e10) number / 1000.0 else number
                val secondsPart = kotlin.math.floor(seconds).toLong()
                java.time.Instant.ofEpochSecond(secondsPart, ((seconds - secondsPart) * 1_000_000_000).toLong()).atOffset(java.time.ZoneOffset.UTC)
            } else {
                val iso = raw.replace(' ', 'T').replace(Regex("([+-]\\d{2})(\\d{2})$"), "$1:$2")
                java.time.OffsetDateTime.parse(iso)
            }
            val micros = time.nano / 1000
            val fraction = if (micros == 0) "" else "." + micros.toString().padStart(6, '0')
            val offset = if (time.offset.totalSeconds == 0) "Z" else time.offset.id
            return JsonPrimitive(time.format(java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss", java.util.Locale.ROOT)) + fraction + offset)
        }
        val result = linkedMapOf<String, JsonElement>()
        for ((key, value) in row) {
            if (key in setOf("address", "provider_refreshed_at") && value is JsonNull) continue
            result[key] = if (key == "provider_refreshed_at") timestamp(value) else normalized(value)
        }
        result.putIfAbsent("currency", JsonPrimitive("USD"))
        for (key in listOf("rating", "review_count", "image_url", "description")) result.putIfAbsent(key, JsonNull)
        result["destination"] = JsonPrimitive(city)
        result["city"] = JsonPrimitive(city)
        result["state"] = JsonPrimitive(state)
        result["category"] = JsonPrimitive(category)
        result["preference_matches"] = JsonArray(emptyList())
        result["source"] = JsonPrimitive(if (real) "real_snapshot" else "controlled_mock_fixture")
        if (snapshot != null) {
            result.putIfAbsent("provider_category_labels", JsonArray(emptyList()))
            result["provider"] = JsonPrimitive(snapshot["provider"]!!.jsonPrimitive.content.trim())
            result["snapshot_version"] = JsonPrimitive(snapshot["snapshot_version"]!!.jsonPrimitive.content.trim())
            result["snapshot_fetched_at"] = timestamp(snapshot["snapshot_fetched_at"]!!)
        }
        return JsonObject(result)
    }
    fun cityCount() = cities.size
    fun snapshotCandidateCount(): Int = routes.sumOf { route -> candidates(route["city"]!!.jsonPrimitive.content, route["category"]!!.jsonPrimitive.content)!!.size }
    fun mockCandidateCount(): Int = cities.sumOf { city -> city.jsonObject["candidates"]!!.jsonObject.values.sumOf { it.jsonArray.size } }
}
