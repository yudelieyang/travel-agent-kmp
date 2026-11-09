package travel

import java.util.Locale

/** Bottom-up port of requirements.py:budget_strength, budget_currency, merge_budget_constraints. */
class RequirementFunctions {
    private fun req(line: Int, occurrence: Int = 0) = SourcePatterns.at("agent.requirements", line, occurrence)
    fun budgetStrength(context: String): String {
        val markers = req(425).finditer(context).map { it.groups[0].span[0] to "HARD" } +
            req(435).finditer(context).map { it.groups[0].span[0] to "SOFT" }
        return markers.maxByOrNull { it.first }?.second ?: "UNSPECIFIED"
    }
    fun budgetCurrency(amount: PyRegex.Match): String {
        val value = listOf("code", "currency", "symbol").firstNotNullOfOrNull { amount.named[it]?.value?.takeIf(String::isNotEmpty) } ?: "USD"
        return when (val normalized = PyStr.casefold(value)) {
            "$", "dollar", "dollars" -> "USD"
            "€", "euro", "euros" -> "EUR"
            "£", "pound", "pounds" -> "GBP"
            else -> normalized.uppercase(Locale.ROOT)
        }
    }
    private fun substring(value: String, begin: Int, end: Int): String = value.substring(value.offsetByCodePoints(0, begin), value.offsetByCodePoints(0, end))
    private fun length(value: String): Int = value.codePointCount(0, value.length)
    private fun lastIndexBefore(value: String, mark: Char, before: Int): Int {
        val text = substring(value, 0, before); val i = text.lastIndexOf(mark)
        return if (i < 0) -1 else text.codePointCount(0, i)
    }
    private fun indexAfter(value: String, mark: Char, from: Int): Int {
        val i = value.indexOf(mark, value.offsetByCodePoints(0, from))
        return if (i < 0) -1 else value.codePointCount(0, i)
    }
    // Port of requirements.py:budget_context and has_budget_intent (L469–506).
    fun budgetContext(query: String, amount: PyRegex.Match): Pair<String, String> {
        val start = amount.groups[0].span[0]; val end = amount.groups[0].span[1]
        val boundaries = req(472).finditer(query)
        val starts = ".!?;".map { lastIndexBefore(query, it, start) }.toMutableList()
        starts += boundaries.filter { it.groups[0].span[0] < start }.map {
            if (it.groups[0].value!!.trimStart().startsWith(",")) it.groups[0].span[0] else it.groups[0].span[1] - 1
        }
        val from = (starts.maxOrNull() ?: -1) + 1
        val ends = ".!?;".map { indexAfter(query, it, end) }.filter { it >= 0 }.toMutableList()
        ends += boundaries.filter { it.groups[0].span[0] >= end }.map { it.groups[0].span[0] }
        val until = ends.minOrNull() ?: length(query)
        return substring(query, from, start).trim() to substring(query, end, until).trim()
    }
    fun hasBudgetIntent(query: String, amount: PyRegex.Match): Boolean {
        val (prefix, suffix) = budgetContext(query, amount)
        val matches = req(420).finditer(prefix)
        if (matches.isNotEmpty() && req(499).search(substring(prefix, matches.last().groups[0].span[1], length(prefix))) == null) return true
        val have = req(501).finditer(prefix)
        return have.isNotEmpty() && req(504).search(substring(prefix, have.last().groups[0].span[1], length(prefix))) == null && req(505).match(suffix) != null
    }
    // Port of requirements.py:recognized_money_expressions (L509–515).
    fun recognizedMoneyExpressions(query: String): List<PyRegex.Match> = req(403).finditer(query).filter { amount ->
        listOf("intro", "code", "symbol", "currency").any { !amount.named[it]?.value.isNullOrEmpty() } || hasBudgetIntent(query, amount)
    }
    // Port of requirements.py:monetary_scope (L601–628).
    fun monetaryScope(prefix: String, suffix: String, intro: String = ""): String? {
        val head = "$prefix $intro".trim()
        val tail = suffix.take(30)
        if (req(604).match(tail) != null) return null
        if (req(606).search(head) != null || req(606).search(tail) != null) {
            if (req(609).search("$head $tail") != null) return null
            return "HOTEL_TOTAL"
        }
        if (req(612).search(head) != null || req(624).match(tail) != null) return "TOTAL_TRIP"
        return null
    }
    // Port of requirements.py:unsupported_money_scope and ambiguous_hotel_budget_phrase (L631–656).
    fun unsupportedMoneyScope(prefix: String, suffix: String): String? {
        val text = "$prefix ${suffix.take(30)}"
        if (req(642, 1).search(text) != null && req(642, 0).search(text) != null) return "HOTEL_PER_NIGHT"
        if (req(646).search(text) != null) return "FOOD_TOTAL"
        if (req(648).search(text) != null) return "TRANSPORT_TOTAL"
        if (req(650).search(text) != null) return "ATTRACTIONS_TOTAL"
        return null
    }
    private fun ambiguousHotelBudget(prefix: String, intro: String, suffix: String): Boolean {
        val text = "$prefix $intro ${suffix.take(30)}"
        return req(635).search(text) != null && req(636).search(text) == null
    }
    // Port of requirements.py:extract_budget_constraints (L518–584).
    fun extractBudgetConstraints(query: String, amounts: List<PyRegex.Match>): Pair<List<BudgetConstraint>, List<RequirementAmbiguity>> {
        val candidates = mutableListOf<BudgetConstraint>()
        val ambiguities = mutableListOf<RequirementAmbiguity>()
        var previousScope: String? = null; var previousEnd: Int? = null
        for (amount in amounts) {
            var created = false
            val (prefix, suffix) = budgetContext(query, amount)
            val intro = amount.named["intro"]?.value ?: ""
            val text = "$prefix ${amount.groups[0].value} $suffix".trim()
            var scope = monetaryScope(prefix, suffix, intro)
            val start = amount.groups[0].span[0]
            val correction = previousEnd != null && req(590).fullmatch(substring(query, previousEnd, start)) != null
            if (scope == null && previousScope != null && correction) scope = previousScope
            var strength = budgetStrength("$prefix $intro $suffix")
            if (scope == "TOTAL_TRIP" && req(537).search(prefix) != null) strength = "HARD"
            if (correction && strength == "UNSPECIFIED") strength = "HARD"
            val number = (amount.named["amount"]!!.value!!.replace(",", "")).toBigDecimal()
                .multiply(if (amount.named["multiplier"]?.value != null) java.math.BigDecimal("1000") else java.math.BigDecimal.ONE)
            val unsupported = unsupportedMoneyScope(prefix, suffix)
            if (scope == "HOTEL_TOTAL" && ambiguousHotelBudget(prefix, intro, suffix)) {
                ambiguities.add(RequirementAmbiguity("BLOCKING", "Hotel budget does not specify a hard limit or a nightly/total policy", text))
            } else if (scope != null && strength == "HARD") {
                require(number.signum() >= 0)
                candidates.add(BudgetConstraint(1.0, budgetCurrency(amount), "deterministic", "BUDGET", "LTE", scope, text, "HARD", number))
                previousScope = scope; created = true
            } else if (unsupported != null) {
                ambiguities.add(RequirementAmbiguity(if (strength == "HARD") "BLOCKING" else "ASSUMABLE", "$unsupported budget is not supported by the deterministic planner", text))
            } else if (scope == "HOTEL_TOTAL" && strength == "UNSPECIFIED") {
                ambiguities.add(RequirementAmbiguity("BLOCKING", "Hotel amount does not specify a hard limit or a nightly/total policy", text))
            }
            if (!created) previousScope = null
            previousEnd = amount.groups[0].span[1]
        }
        return mergeBudgetConstraints(candidates) to ambiguities
    }
    // Port of requirements.py:preference_category (L762–775).
    fun preferenceCategory(value: String): String {
        if (req(763).fullmatch(value) != null) return "TRANSPORT"
        if (req(765).search(value) != null || req(767).search(value) != null) return "ACTIVITY"
        if (req(769, 0).search(value) != null || req(769, 1).match(value) != null) return "FOOD"
        if (req(773).search(value) != null) return "HOTEL"
        return "UNSPECIFIED"
    }
    // Port of requirements.py:extract_specific_preferences (L709–732).
    fun extractSpecificPreferences(query: String): List<SpecificPreference> {
        val generic = setOf("museums", "food", "parks", "history", "art", "quiet", "central", "budget", "luxury", "vegetarian", "vegan", "seafood", "walking", "public transit", "taxi")
        val preferences = mutableListOf<SpecificPreference>()
        for (match in req(413).finditer(query)) {
            for (rawValue in req(416).split(match.named.getValue("values").value!!)) {
                val raw = rawValue ?: continue
                if (req(403).search(raw) != null) continue
                var value = PyStr.casefold(req(417).sub(raw.trim(), "").trim())
                if (value.isEmpty() || value in generic) continue
                if (value == "zoos") value = "zoo"
                val category = preferenceCategory(value)
                if (category == "FOOD" && value.endsWith(" food")) value = value.removeSuffix(" food").trim()
                val item = SpecificPreference(category, value)
                if (item !in preferences) preferences.add(item)
            }
        }
        for (match in req(725).finditer(query)) {
            val value = PyStr.casefold(match.named.getValue("value").value!!)
            val item = SpecificPreference("ACTIVITY", if (value == "zoos") "zoo" else value)
            if (item !in preferences) preferences.add(item)
        }
        return preferences
    }
    // Port of requirements.py:extract_budget_preferences (L735–759).
    fun extractBudgetPreferences(query: String, amounts: List<PyRegex.Match>): List<SpecificPreference> {
        val preferences = mutableListOf<SpecificPreference>()
        for (amount in amounts) {
            val (prefix, suffix) = budgetContext(query, amount)
            val intro = amount.named["intro"]?.value ?: ""
            val scope = monetaryScope(prefix, suffix, intro)
            val category = if (scope == "HOTEL_TOTAL" || req(743).search("$prefix ${suffix.take(30)}") != null) "HOTEL"
                else if (req(745).search("$prefix ${suffix.take(30)}") != null) "FOOD" else null
            if (category == null || budgetStrength("$prefix $intro $suffix") != "SOFT") continue
            val rate = if (req(752).search(suffix) != null) " per night" else ""
            val value = PyStr.casefold("around ${budgetCurrency(amount)} ${amount.named["amount"]!!.value}$rate")
            val item = SpecificPreference(category, value)
            if (item !in preferences) preferences.add(item)
        }
        return preferences
    }
    fun mergeBudgetConstraints(items: List<BudgetConstraint>): List<BudgetConstraint> {
        val merged = linkedMapOf<String, BudgetConstraint>()
        for (item in items) merged[item.scope] = item
        return merged.values.toList()
    }
}
