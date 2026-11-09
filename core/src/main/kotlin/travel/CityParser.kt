package travel

import java.util.regex.Pattern
import kotlinx.serialization.json.*

/** Port of candidate_data.py city_at_start and resolve_city (L70–106). */
class CityParser {
    private val dataset: JsonObject = requireNotNull(javaClass.classLoader.getResourceAsStream("data/travel/us/cities.json"))
        .bufferedReader(Charsets.UTF_8).use { Json.parseToJsonElement(it.readText()).jsonObject }
    private val cities = dataset["cities"]!!.jsonArray
    private fun key(value: String) = PyRegex("[a-z0-9]+").finditer(PyStr.casefold(value)).joinToString(" ") { it.groups[0].value!! }
    private fun aliases(city: JsonObject): List<String> = listOf(city["city"]!!.jsonPrimitive.content) + city["aliases"]!!.jsonArray.map { it.jsonPrimitive.content }
    private fun project(city: JsonObject): JsonObject = JsonObject(city.mapValues { (name, value) ->
        if (name != "candidates") value else JsonObject(value.jsonObject.mapValues { (_, list) ->
            JsonArray(list.jsonArray.map { entry ->
                val row = entry.jsonObject.toMutableMap()
                row.putIfAbsent("currency", JsonPrimitive("USD"))
                listOf("rating", "review_count", "image_url", "description").forEach { row.putIfAbsent(it, JsonNull) }
                JsonObject(row)
            })
        })
    })
    fun resolveCity(value: String): JsonObject? = cities.firstOrNull { row -> aliases(row.jsonObject).any { key(it) == key(value) } }?.let { project(it.jsonObject) }
    fun cityAtStart(value: String): Pair<JsonObject, Int>? {
        var found: JsonObject? = null; var longest = -1
        for (row in cities) for (alias in aliases(row.jsonObject)) {
            val pattern = "^" + key(alias).split(' ').joinToString("[\\s,.]+") { Pattern.quote(it) } + "\\b"
            val end = PyRegex(pattern, 2).match(value)?.groups?.first()?.span?.get(1) ?: continue
            if (end > longest) { longest = end; found = row.jsonObject }
        }
        return found?.let { project(it) to longest }
    }
}
