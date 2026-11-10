package travel

/** Insert a destination or replace the destination already extracted by the engine. */
internal fun selectCity(query: String, city: String): String {
    if (query.isBlank()) return city
    if (Regex("\\b(?:to|in)\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(query)) return query.trimEnd() + " " + city
    val destination = runCatching { RequirementsParser().parse(query).destination }.getOrNull()
    if (!destination.isNullOrBlank()) {
        val normalized = destination.replaceFirst(Regex("^(?:to|in)\\s+", RegexOption.IGNORE_CASE), "")
        val candidates = (CitySuggestions.aliases(normalized) + normalized).distinct().sortedByDescending { it.length }
        for (candidate in candidates) {
            val pattern = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(candidate) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
            val found = pattern.find(query) ?: continue
            return query.replaceRange(found.range, city)
        }
    }
    val trimmed = query.trimEnd()
    val punctuation = trimmed.takeLastWhile { it == '.' || it == '!' || it == '?' }
    return trimmed.dropLast(punctuation.length) + " in " + city + punctuation
}
