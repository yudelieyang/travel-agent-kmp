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
    fun mergeBudgetConstraints(items: List<BudgetConstraint>): List<BudgetConstraint> {
        val merged = linkedMapOf<String, BudgetConstraint>()
        for (item in items) merged[item.scope] = item
        return merged.values.toList()
    }
}
