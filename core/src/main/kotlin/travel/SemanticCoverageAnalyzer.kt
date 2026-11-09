package travel

/** Port of semantic_coverage.py:SemanticCoverageAnalyzer.analyze (L97–184). */
class SemanticCoverageAnalyzer {
    private val f = CoverageFunctions()
    private fun rx(line: Int) = SourcePatterns.at("agent.semantic_coverage", line)
    fun analyze(query: String, requirements: TravelRequirements): CoverageResult {
        val reasons = mutableListOf<String>(); val evidence = mutableListOf<String>()
        val ambiguities = requirements.requirements_v2.ambiguities
        for (ambiguity in ambiguities) {
            if (ambiguity.level != "BLOCKING" || "not supported" in ambiguity.reason) continue
            if ("AMBIGUOUS_SCOPE" !in reasons) reasons.add("AMBIGUOUS_SCOPE")
            if (ambiguity.source_text !in evidence) evidence.add(ambiguity.source_text)
        }
        val tradeoff = rx(39).search(query) ?: if (rx(38).search(query) != null) rx(48).search(query) else null
        if (tradeoff != null) { reasons.add("TRADEOFF_LANGUAGE"); evidence.add(tradeoff.groups[0].value!!) }
        val exclusion = f.unrepresentedExclusion(query)
        if (exclusion != null) { reasons.add("UNREPRESENTED_NEGATION"); evidence.add(exclusion) }
        val preference = rx(87).search(query)
        val suffix = requirements.destination?.let { " in ${PyStr.casefold(it)}" }
        if (preference != null && ambiguities.isEmpty() &&
            (requirements.requirements_v2.preferences.isEmpty() || (suffix != null && requirements.requirements_v2.preferences.any { suffix in it.value }))) {
            reasons.add("UNRESOLVED_REQUIREMENT_CLAUSE"); evidence.add(preference.groups[0].value!!)
        }
        val softHotel = rx(72).search(query)
        if (softHotel != null && requirements.requirements_v2.constraints.any { it.scope == "TOTAL_TRIP" } &&
            requirements.requirements_v2.preferences.any { it.category == "HOTEL" }) {
            reasons.add("UNRESOLVED_REQUIREMENT_CLAUSE"); evidence.add(softHotel.groups[0].value!!)
        }
        val gaps = f.unrepresentedHardMoney(query, requirements)
        val softRejection = rx(63).search(query)
        if (gaps.isNotEmpty() || (softRejection != null && requirements.requirements_v2.constraints.isNotEmpty())) {
            reasons.add("MONETARY_SEMANTIC_GAP")
            if (gaps.isNotEmpty()) evidence.addAll(gaps) else evidence.add(softRejection!!.groups[0].value!!)
        }
        val correction = f.unresolvedCorrection(query, requirements)
        if (correction != null) { reasons.add("CORRECTION_TARGET_UNRESOLVED"); evidence.add(correction) }
        val hotel = rx(67).search(query)
        if (hotel != null && requirements.requirements_v2.constraints.isEmpty() && requirements.requirements_v2.preferences.isEmpty() &&
            ambiguities.isEmpty() && rx(64).search(hotel.groups[0].value!!) == null) {
            reasons.add("AMBIGUOUS_SCOPE"); evidence.add(hotel.groups[0].value!!)
        }
        return CoverageResult(reasons.isNotEmpty(), reasons, evidence, CoverageSignals(
            ambiguities.count { it.level == "BLOCKING" }, f.budgetLikeAmounts(query).size, requirements.requirements_v2.constraints.size))
    }
}
