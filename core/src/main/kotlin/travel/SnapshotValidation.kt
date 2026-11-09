package travel

import java.time.OffsetDateTime
import kotlinx.serialization.json.*

/** Port of candidate_providers.py SnapshotCandidate, SnapshotCity, CandidateSnapshot and manifest validators. */
internal object SnapshotValidation {
    private val categories = mapOf("attractions" to "per_person_visit", "hotel" to "per_person_night", "food" to "per_person_meal", "transport" to "per_person_day")
    private val version = Regex("^[a-z0-9][a-z0-9._-]*$")
    private fun obj(e: JsonElement) = e.jsonObject
    private fun allowed(o: JsonObject, keys: Set<String>) { require(o.keys.all { it in keys }) { "Unexpected field" } }
    private fun string(o: JsonObject, key: String, optional: Boolean = false): String? {
        val v = o[key]
        if (optional && (v == null || v is JsonNull)) return null
        val p = v as? JsonPrimitive ?: error("Missing $key")
        require(p.isString) { "Invalid $key" }
        return p.content.trim()
    }
    private fun required(o: JsonObject, key: String) = requireNotNull(string(o, key)).also { require(it.isNotEmpty()) { "Empty $key" } }
    private fun literal(o: JsonObject, key: String): String {
        val value = o[key] as? JsonPrimitive ?: error("Missing $key")
        require(value.isString); return value.content
    }
    private fun nonemptyOptional(o: JsonObject, key: String) { string(o, key, true)?.let { require(it.isNotEmpty()) } }
    private fun number(o: JsonObject, key: String, optional: Boolean = false): Double? {
        val v = o[key]
        if (optional && (v == null || v is JsonNull)) return null
        val text = (v as? JsonPrimitive)?.content ?: error("Invalid $key")
        val n = when { !v.jsonPrimitive.isString && text == "true" -> 1.0; !v.jsonPrimitive.isString && text == "false" -> 0.0; else -> text.toDoubleOrNull() ?: error("Invalid $key") }
        require(n.isFinite()); return n
    }
    private fun strings(o: JsonObject, key: String, required: Boolean = true): List<String> {
        val a = o[key] ?: if (required) error("Missing $key") else return emptyList()
        return a.jsonArray.map { require((it as? JsonPrimitive)?.isString == true); it.jsonPrimitive.content.trim() }
    }
    private fun timestamp(o: JsonObject, key: String, optional: Boolean = false) {
        val value = o[key]
        if (optional && (value == null || value is JsonNull)) return
        val primitive = value as? JsonPrimitive ?: error("Invalid $key")
        val raw = primitive.content
        require(raw == raw.trim())
        val numeric = raw.toDoubleOrNull()
        if (numeric != null) {
            require(numeric.isFinite())
            val seconds = if (kotlin.math.abs(numeric) > 2e10) numeric / 1000.0 else numeric
            val year = java.time.Instant.ofEpochSecond(seconds.toLong()).atOffset(java.time.ZoneOffset.UTC).year
            require(year in 1..9999)
        } else {
            require(primitive.isString)
            val normalized = raw.replace(' ', 'T').replace(Regex("([+-]\\d{2})(\\d{2})$"), "$1:$2")
            OffsetDateTime.parse(normalized)
        }
    }
    // Port of candidate_data.py CandidateFixture, CityFixture and CandidateDataset validators.
    fun fixtureDataset(data: JsonObject) {
        fun plain(o: JsonObject, key: String): String {
            val value = o[key] as? JsonPrimitive ?: error("Missing $key")
            require(value.isString); return value.content
        }
        allowed(data, setOf("dataset", "source", "cities")); plain(data, "dataset"); require(plain(data, "source") == "controlled_mock_fixture")
        val ids = mutableSetOf<String>(); val owners = mutableMapOf<String, String>()
        for (entry in data["cities"]!!.jsonArray) {
            val city = entry.jsonObject; allowed(city, setOf("city", "state", "aliases", "candidates"))
            val name = plain(city, "city"); require(plain(city, "state").length == 2)
            val aliases = city["aliases"]!!.jsonArray.map { require(it.jsonPrimitive.isString); it.jsonPrimitive.content }
            for (alias in listOf(name) + aliases) {
                val key = Regex("[a-z0-9]+").findAll(PyStr.casefold(alias)).joinToString(" ") { it.value }
                val owner = owners.putIfAbsent(key, name); require(owner == null || owner == name)
            }
            val groups = city["candidates"]!!.jsonObject; require(groups.keys == categories.keys && groups.values.all { it.jsonArray.isNotEmpty() })
            for (rows in groups.values) for (item in rows.jsonArray) {
                val row = item.jsonObject
                allowed(row, setOf("id", "name", "price", "currency", "unit", "tags", "rating", "review_count", "image_url", "description"))
                require(ids.add(plain(row, "id"))); plain(row, "name"); require(number(row, "price")!! >= 0)
                require((if ("currency" in row) plain(row, "currency") else "USD") == "USD")
                require(plain(row, "unit") in categories.values)
                row["tags"]!!.jsonArray.forEach { require(it.jsonPrimitive.isString) }
                number(row, "rating", true)?.let { require(it in 0.0..5.0) }
                number(row, "review_count", true)?.let { require(it >= 0 && it % 1.0 == 0.0) }
                for (key in listOf("image_url", "description")) if (key in row && row[key] !is JsonNull) plain(row, key)
            }
        }
    }
    fun manifest(data: JsonObject): List<JsonObject> {
        allowed(data, setOf("manifest_version", "routes")); require(version.matches(required(data, "manifest_version")))
        val routes = data["routes"]!!.jsonArray.map { obj(it) }; require(routes.isNotEmpty())
        val seen = mutableSetOf<Pair<String, String>>()
        routes.forEach { r ->
            allowed(r, setOf("city", "category", "snapshot")); val city = required(r, "city"); val cat = literal(r, "category")
            require(cat in categories); required(r, "snapshot"); require(seen.add(PyStr.casefold(city) to cat))
        }
        return routes
    }
    fun snapshot(data: JsonObject, cityRegistry: (String) -> Pair<String, String>?) {
        allowed(data, setOf("snapshot_version", "snapshot_fetched_at", "provider", "source", "provider_data_timestamp", "record_count", "category_map_version", "cost_method_version", "attribution", "license", "cities"))
        require(version.matches(required(data, "snapshot_version")))
        timestamp(data, "snapshot_fetched_at"); timestamp(data, "provider_data_timestamp", true)
        val provider = required(data, "provider"); require(version.matches(provider)); require(literal(data, "source") == "real_snapshot")
        listOf("category_map_version", "cost_method_version", "attribution", "license").forEach { nonemptyOptional(data, it) }
        val count = number(data, "record_count", true); if (count != null) require(count >= 1 && count % 1.0 == 0.0)
        val cities = data["cities"]!!.jsonArray; require(cities.isNotEmpty())
        val ids = mutableSetOf<String>(); val providerIds = mutableSetOf<String>(); val cityNames = mutableSetOf<String>()
        for (entry in cities) {
            val city = obj(entry); allowed(city, setOf("city", "state", "candidates"))
            val name = required(city, "city"); val state = required(city, "state"); require(state.length == 2)
            require(cityNames.add(PyStr.casefold(name))); require(cityRegistry(name) == (name to state))
            val groups = city["candidates"]!!.jsonObject; require(groups.isNotEmpty())
            for ((cat, items) in groups) {
                require(cat in categories); val rows = items.jsonArray; require(rows.isNotEmpty())
                for (entry2 in rows) {
                    val row = obj(entry2)
                    allowed(row, setOf("id", "provider_place_id", "name", "address", "latitude", "longitude", "provider_category_ids", "provider_category_labels", "provider_refreshed_at", "price", "currency", "unit", "tags", "rating", "review_count", "image_url", "description", "cost_origin", "cost_method", "cost_version"))
                    val id = required(row, "id"); val pid = required(row, "provider_place_id"); required(row, "name")
                    require(ids.add(id)); require(providerIds.add(pid))
                    if (provider == "openstreetmap") require(Regex("(node|way|relation)/[1-9][0-9]*").matches(pid))
                    require(number(row, "latitude")!! in -90.0..90.0); require(number(row, "longitude")!! in -180.0..180.0)
                    require(strings(row, "provider_category_ids").isNotEmpty()); strings(row, "provider_category_labels", false); strings(row, "tags")
                    timestamp(row, "provider_refreshed_at", true); require(number(row, "price")!! >= 0.0)
                    require((if ("currency" in row) literal(row, "currency") else "USD") == "USD"); require(literal(row, "unit") == categories[cat])
                    number(row, "rating", true)?.let { require(it in 0.0..5.0) }
                    number(row, "review_count", true)?.let { require(it >= 0 && it % 1.0 == 0.0) }
                    listOf("address", "image_url", "description").forEach { string(row, it, true) }
                    require(literal(row, "cost_origin") == "planner_estimate"); required(row, "cost_method"); required(row, "cost_version")
                }
            }
        }
        if (count != null) require(count == ids.size.toDouble())
    }
}
