package travel

import kotlinx.serialization.json.*

/** Bottom-up port of semantic_coverage.py (L187–269). */
class CoverageFunctions {
    data class Amount(val value: Double, val start: Int, val end: Int, val clause: String)
    private fun regex(line: Int) = SourcePatterns.at("agent.semantic_coverage", line)
    private fun cpLength(value: String) = value.codePointCount(0, value.length)
    private fun slice(value: String, from: Int, until: Int): String = value.substring(value.offsetByCodePoints(0, from), value.offsetByCodePoints(0, until))
    private fun find(value: String, char: Char, start: Int): Int {
        val i = value.indexOf(char, value.offsetByCodePoints(0, start))
        return if (i < 0) -1 else value.codePointCount(0, i)
    }
    private fun rfind(value: String, char: Char, end: Int): Int {
        val text = slice(value, 0, end); val i = text.lastIndexOf(char)
        return if (i < 0) -1 else text.codePointCount(0, i)
    }
    // Port of semantic_coverage.py:budget_like_amounts (L187–203).
    fun budgetLikeAmounts(query: String): List<Amount> {
        val result = mutableListOf<Amount>()
        for (match in regex(51).finditer(query)) {
            val value = pyFloatParseAmount(match.named.getValue("value").value!!.replace(",", ""))
            if (value < 50.0) continue
            val begin = match.groups[0].span[0]; val end = match.groups[0].span[1]
            val start = ".!?;".maxOf { rfind(query, it, begin) } + 1
            val stop = ".!?;".map { find(query, it, end) }.filter { it >= 0 }.minOrNull() ?: cpLength(query)
            val clause = slice(query, start, stop).trim()
            if (regex(52).search(clause) != null && regex(57).search(clause) != null && regex(64).search(clause) == null)
                result.add(Amount(value, begin, end, clause))
        }
        return result
    }
    // Port of semantic_coverage.py:unrepresented_hard_money (L206–229).
    fun unrepresentedHardMoney(query: String, requirements: TravelRequirements): List<String> {
        val represented = requirements.requirements_v2.constraints.map { pyFloatFromDecimal(it.value) }.toMutableList()
        if (requirements.budget_amount != null && requirements.budget_constraint_strength == "HARD") represented.add(pyFloatFromDecimal(requirements.budget_amount))
        for (ambiguity in requirements.requirements_v2.ambiguities) {
            represented += regex(214).finditer(ambiguity.source_text).map { pyFloatParseAmount(it.groups[0].value!!.replace(",", "")) }
        }
        val gaps = mutableListOf<String>()
        for (item in budgetLikeAmounts(query)) {
            if (represented.any { pyFloatIeeeEqual(it, item.value) }) continue
            val remainder = slice(query, item.end, cpLength(query))
            if (regex(62).search(remainder) != null) {
                val later = regex(51).finditer(remainder).map { pyFloatParseAmount(it.named.getValue("value").value!!.replace(",", "")) }
                if (later.any { candidate -> represented.any { pyFloatIeeeEqual(it, candidate) } }) continue
            }
            if (item.clause !in gaps) gaps.add(item.clause)
        }
        return gaps
    }
    // Port of semantic_coverage.py:unresolved_correction (L232–262).
    fun unresolvedCorrection(query: String, requirements: TravelRequirements): String? {
        for (cue in regex(62).finditer(query)) {
            val stop = ".!?;".map { find(query, it, cue.groups[0].span[1]) }.filter { it >= 0 }.minOrNull() ?: cpLength(query)
            val clause = slice(query, cue.groups[0].span[0], stop).trim()
            val amounts = regex(51).finditer(clause).filter { match ->
                regex(243).match(slice(clause, match.groups[0].span[1], cpLength(clause))) == null &&
                    regex(244).search(slice(clause, 0, match.groups[0].span[0])) == null
            }
            val amount = amounts.firstOrNull() ?: continue
            val value = pyFloatParseAmount(amount.named.getValue("value").value!!.replace(",", ""))
            val scope = if (regex(252).search(clause) != null) "TOTAL_TRIP" else if (regex(254).search(clause) != null) "HOTEL_TOTAL" else null
            if (scope != null) {
                if (requirements.requirements_v2.constraints.none { it.scope == scope && pyFloatIeeeEqual(pyFloatFromDecimal(it.value), value) }) return clause
            } else if (requirements.requirements_v2.constraints.none { pyFloatIeeeEqual(pyFloatFromDecimal(it.value), value) }) return clause
        }
        return null
    }
    // Port of semantic_coverage.py:unrepresented_exclusion (L265–269).
    fun unrepresentedExclusion(query: String): String? = regex(266).split(query).filterNotNull().firstOrNull { clause ->
        regex(77).search(clause) != null && regex(82).search(clause) != null
    }?.trim()
}
