package travel

internal data class InputPreview(val requirements: TravelRequirements, val coverage: CoverageResult, val unknownCity: Boolean)

internal fun parseInputPreview(query: String): InputPreview {
    val normalized = query.trim()
    require(normalized.isNotEmpty() && normalized.codePointCount(0, normalized.length) <= 2000)
    val requirements = RequirementsParser().parse(normalized)
    return InputPreview(requirements, SemanticCoverageAnalyzer().analyze(normalized, requirements), requirements.destination?.let { CityParser().resolveCity(it) == null } ?: false)
}
