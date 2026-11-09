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
