package travel

import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.jsonPrimitive

/** Port of requirements.py:parse_requirements (L218–385), including model projections. */
class RequirementsParser {
    private val f = RequirementFunctions()
    private val cities = CityParser()
    private fun rx(line: Int, occurrence: Int = 0) = SourcePatterns.at("agent.requirements", line, occurrence)
    private fun variant(line: Int, value: String) = SourcePatterns.variant("agent.requirements", line, value)
    private fun cpLen(s: String) = s.codePointCount(0, s.length)
    private fun slice(s: String, start: Int, end: Int = cpLen(s)): String = s.substring(s.offsetByCodePoints(0, start), s.offsetByCodePoints(0, end))
    private val numbers = "one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty".split(' ')
    private fun number(s: String): Int { val i = numbers.indexOf(PyStr.lower(s)); return if (i >= 0) i + 1 else s.toInt() }
    private fun normalizePlace(value: String?): String? {
        val s = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return cities.resolveCity(s)?.get("city")?.jsonPrimitive?.content ?: s
    }
    fun parse(queryInput: String): TravelRequirements {
        var query = PyRegex("\\s+").split(queryInput.replace('’', '\'').replace("\n", ";").trim()).filterNotNull().joinToString(" ")
        query = rx(221).sub(query, "\\1 trip")
        query = rx(222).sub(query, "more than")
        query = rx(223).sub(query, "\\1\\2")
        query = rx(224).sub(query, "\\1 \\2")
        var destination: String? = null
        val constraints = mutableListOf<String>()
        val place = rx(226).search(query)
        if (place != null) {
            destination = place.groups[1].value!!.trim()
            if (" in " in PyStr.casefold(destination)) destination = destination.substringAfterLast(" in ").trim()
        } else {
            val short = cities.cityAtStart(query)
            if (short != null) {
                destination = short.first["city"]!!.jsonPrimitive.content
                if (rx(242).match(slice(query, short.second)) != null) { destination = null; constraints.add("ambiguous destination") }
            }
            if (short == null) destination = rx(246).search(query)?.named?.get("place")?.value
        }
        if (destination != null && rx(254).search(destination) != null) { destination = null; constraints.add("ambiguous destination") }
        else if (destination != null && rx(257).match(destination) != null) destination = null
        val origin = rx(261).search(query)?.groups?.get(1)?.value?.trim()
        val duration = rx(264).search(query)?.groups?.get(1)?.value?.let(::number)
        val people = rx(267).search(query) ?: rx(271).search(query) ?: rx(273).search(query)
        val travelers = people?.groups?.get(1)?.value?.let(::number) ?: when {
            rx(278).search(query) != null || rx(280).search(query) != null -> 1
            rx(282).search(query) != null -> 2
            else -> null
        }
        val dates = rx(284).finditer(query).map { it.groups[0].value!! }
        require(dates.size <= 2)
        val startDate = dates.getOrNull(0)?.let(LocalDate::parse)
        val endDate = dates.getOrNull(1)?.let(LocalDate::parse)
        if (startDate != null && endDate != null) {
            val days = ChronoUnit.DAYS.between(startDate, endDate) + 1
            require(days in 1..30 && (duration == null || duration.toLong() == days))
        }
        if (duration != null) require(duration in 1..30)
        if (travelers != null) require(travelers in 1..20)
        val amounts = f.recognizedMoneyExpressions(query)
        val extracted = f.extractBudgetConstraints(query, amounts)
        var budget: BigDecimal? = null; var scope = "UNKNOWN"; var strength = "UNSPECIFIED"; var currency = "USD"
        val legacy = f.legacyBudgetProjectionMatch(query, amounts)
        if (legacy != null && extracted.first.isEmpty()) {
            val mult = if (legacy.named["multiplier"]?.value != null) BigDecimal("1000") else BigDecimal.ONE
            budget = legacy.named["amount"]!!.value!!.replace(",", "").toBigDecimal().multiply(mult)
            require(budget.signum() >= 0)
            val (prefix, suffix) = f.budgetContext(query, legacy)
            val intro = PyStr.casefold(legacy.named["intro"]?.value ?: "")
            if (rx(306).match(suffix) != null) scope = "PER_PERSON"
            else if (rx(308).match(suffix) != null || rx(312).search("$prefix $intro") != null) scope = "TOTAL_TRIP"
            strength = f.budgetStrength("$prefix $intro")
            if (strength == "HARD" && f.budgetStrength("$prefix $intro $suffix") == "SOFT") strength = "SOFT"
            if (strength == "SOFT") constraints.add("approximate budget")
            currency = f.budgetCurrency(legacy)
        } else if (amounts.isEmpty()) {
            val invalid = rx(461).search(query)
            val value = invalid?.named?.get("value")?.value
            require(value == null || PyStr.casefold(value) in setOf("hotel", "hotels"))
        }
        val objective = if (rx(328).search(query) != null) "maximize_budget_utilization" else null
        val vocabulary = linkedMapOf(
            "interests" to listOf("museums", "food", "parks", "history", "art"),
            "hotel_preferences" to listOf("quiet", "central", "budget", "luxury"),
            "food_preferences" to listOf("vegetarian", "vegan", "seafood"),
            "transport_preferences" to listOf("walking", "public transit", "taxi")
        )
        val values = vocabulary.mapValues { mutableListOf<String>() }
        val clauses = rx(340).split(PyStr.lower(query)).filterNotNull()
        for ((field, terms) in vocabulary) for (term in terms) for (clause in clauses) {
            val match = variant(346, term).search(clause) ?: continue
            if (term == "budget" && rx(347).search(clause) == null) continue
            if (term == "walking" && rx(349).search(clause) != null) continue
            if (rx(351).search(slice(clause, 0, match.groups[0].span[0])) != null) constraints.add(if (term == "walking") "no walking" else "avoid $term")
            else if (term !in values.getValue(field)) values.getValue(field).add(term)
        }
        val negatives = linkedMapOf("\\bnightlife\\b" to "no nightlife", "\\bmeat\\b" to "no meat", "\\b(?:rent a car|rental cars?)\\b" to "no rental car", "\\bexpensive restaurants?\\b" to "avoid expensive restaurants")
        for (clause in clauses) {
            for ((pattern, label) in negatives) {
                val match = variant(357, pattern).search(clause)
                if (match != null && rx(358).search(slice(clause, 0, match.groups[0].span[0])) != null) constraints.add(label)
            }
            if (rx(360).search(clause) != null) constraints.add("wheelchair")
            val must = rx(362).search(clause)
            if (must != null && amounts.none { it.groups[0].value!! in clause }) constraints.add(must.groups[0].value!!.trim())
            if (rx(365).search(clause) != null) constraints.add(clause.trim())
        }
        val specifics = f.extractSpecificPreferences(query) + f.extractBudgetPreferences(query, amounts)
        for (preference in specifics) {
            val field = when (preference.category) { "FOOD" -> "food_preferences"; "ACTIVITY" -> "interests"; "TRANSPORT" -> "transport_preferences"; "HOTEL" -> "hotel_preferences"; else -> null }
            if (field != null && preference.value !in values.getValue(field)) values.getValue(field).add(preference.value)
            if (preference.category == "FOOD" && "food" !in values.getValue("interests")) values.getValue("interests").add("food")
        }
        val canonical = extracted.first.toMutableList()
        for (constraint in canonical) require(constraint.strength == "HARD") { "Canonical budget constraints must be hard constraints" }
        require(canonical.map { it.scope }.distinct().size == canonical.size) { "Only one canonical budget constraint is allowed per scope" }
        if (canonical.isEmpty() && budget != null && scope == "TOTAL_TRIP" && strength == "HARD") {
            canonical.add(BudgetConstraint(1.0, currency, "LEGACY_PROJECTION", "BUDGET", "LTE", "TOTAL_TRIP", null, "HARD", budget))
        }
        val canonicalTotal = canonical.firstOrNull { it.scope == "TOTAL_TRIP" }
        if (canonicalTotal != null) { budget = canonicalTotal.value; scope = "TOTAL_TRIP"; strength = "HARD"; currency = canonicalTotal.currency }
        val v2 = RequirementsV2(extracted.second, canonical, objective?.let(::listOf) ?: emptyList(), specifics)
        return TravelRequirements(budget, strength, scope, constraints.distinct(), currency, normalizePlace(destination), duration, endDate?.toString(),
            values.getValue("food_preferences"), values.getValue("hotel_preferences"), values.getValue("interests"), objective, normalizePlace(origin), v2, specifics,
            startDate?.toString(), values.getValue("transport_preferences"), travelers)
    }
}
