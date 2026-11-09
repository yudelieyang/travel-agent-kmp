package travel

import kotlinx.serialization.json.*

internal object CitySuggestions {
    private data class City(val name: String, val aliases: List<String>)
    private val cities: List<City> by lazy {
        val text = requireNotNull(CityParser::class.java.classLoader.getResourceAsStream("data/travel/us/cities.json")).bufferedReader(Charsets.UTF_8).use { it.readText() }
        Json.parseToJsonElement(text).jsonObject.getValue("cities").jsonArray.map {
            val row = it.jsonObject
            City(row.getValue("city").jsonPrimitive.content, row.getValue("aliases").jsonArray.map { alias -> alias.jsonPrimitive.content })
        }
    }
    val all: List<String> get() = cities.map { it.name }
    private fun key(value: String) = Regex("[a-z0-9]+").findAll(PyStr.casefold(value)).joinToString(" ") { it.value }
    // Unrestricted Damerau–Levenshtein distance, including adjacent transpositions.
    private fun distance(a: String, b: String): Int {
        val max = a.length + b.length
        val d = Array(a.length + 2) { IntArray(b.length + 2) }
        d[0][0] = max
        for (i in 0..a.length) { d[i + 1][0] = max; d[i + 1][1] = i }
        for (j in 0..b.length) { d[0][j + 1] = max; d[1][j + 1] = j }
        val last = mutableMapOf<Char, Int>()
        for (i in 1..a.length) {
            var matched = 0
            for (j in 1..b.length) {
                val i1 = last[b[j - 1]] ?: 0; val j1 = matched
                val cost = if (a[i - 1] == b[j - 1]) { matched = j; 0 } else 1
                d[i + 1][j + 1] = minOf(d[i][j] + cost, d[i + 1][j] + 1, d[i][j + 1] + 1, d[i1][j1] + (i - i1 - 1) + 1 + (j - j1 - 1))
            }
            last[a[i - 1]] = i
        }
        return d[a.length + 1][b.length + 1]
    }
    fun closest(input: String): List<String> {
        val value = key(input); if (value.isEmpty()) return emptyList()
        val limit = if (value.length <= 4) 1 else 2
        return cities.mapIndexed { index, city -> Triple(city.name, (listOf(city.name) + city.aliases).minOf { distance(value, key(it)) }, index) }
            .filter { it.second <= limit }.sortedWith(compareBy({ it.second }, { it.third })).take(3).map { it.first }
    }
}
