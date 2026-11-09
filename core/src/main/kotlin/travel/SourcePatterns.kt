package travel
import kotlinx.serialization.json.*

/** Patterns are extracted from golden-v1 by tools/generate_regex_parity.py; each records its source line. */
internal object SourcePatterns {
    private val patterns = requireNotNull(javaClass.classLoader.getResourceAsStream("parser/regex-patterns.json"))
        .bufferedReader(Charsets.UTF_8).use { Json.parseToJsonElement(it.readText()).jsonObject["patterns"]!!.jsonArray }
    private val cache = mutableMapOf<String, PyRegex>()
    fun variant(module: String, line: Int, value: String): PyRegex {
        val origin = "app.$module:$line:$value"
        return synchronized(cache) { cache.getOrPut(origin) {
            val item = patterns.first { entry -> entry.jsonObject["origins"]!!.jsonArray.any { it.jsonPrimitive.content == origin } }.jsonObject
            PyRegex(item["pattern"]!!.jsonPrimitive.content, item["flags"]!!.jsonPrimitive.int)
        } }
    }
    fun at(module: String, line: Int, occurrence: Int = 0): PyRegex {
        val origin = "app.$module:$line"
        val key = "$origin#$occurrence"
        return synchronized(cache) { cache.getOrPut(key) {
            val entries = patterns.filter { item -> item.jsonObject["origins"]!!.jsonArray.any { it.jsonPrimitive.content == origin } }
            val item = entries[occurrence].jsonObject
            PyRegex(item["pattern"]!!.jsonPrimitive.content, item["flags"]!!.jsonPrimitive.int)
        } }
    }
}
