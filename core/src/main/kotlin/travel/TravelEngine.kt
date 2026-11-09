package travel

import java.math.BigDecimal
import java.math.RoundingMode
import java.math.MathContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

private val ZERO = BigDecimal.ZERO
private val DECIMAL_CONTEXT = MathContext(28, RoundingMode.HALF_EVEN)
// Port of mock.py:money (L23–24); Python Decimal.quantize uses the active precision of 28.
internal fun quantizeMoney(value: BigDecimal): BigDecimal {
    val rounded = value.setScale(2, RoundingMode.HALF_EVEN)
    if (rounded.precision() > DECIMAL_CONTEXT.precision) throw ArithmeticException("Decimal quantize exceeds precision 28")
    return rounded
}
private fun money(value: BigDecimal): BigDecimal = quantizeMoney(value)
private val categories = listOf("attractions", "hotel", "food", "transport")
private val toolNames = listOf("search_attractions", "search_hotels", "search_restaurants", "search_transport")
private val json = Json { explicitNulls = true; encodeDefaults = false }

data class Stage(val name: String, val sequence: Int, val outcome: String)
data class SearchArguments(val destination: String, val maxPrice: Double?, val preferences: List<String>)
data class ToolRecord(val toolName: String, val requestedArguments: SearchArguments?, val runtimeArguments: SearchArguments?)
data class EngineRun(val result: PlanResult, val stages: List<Stage>, val tools: List<ToolRecord>, val savings: Double?)

/** Deterministic port of graph.py, itinerary.py, planner.py, mock.py and candidate_data.py. */
class TravelEngine(private val loader: SnapshotLoader = SnapshotLoader()) {
    private fun preferenceKey(s: String): String {
        val value = Regex("[a-z0-9]+").findAll(PyStr.casefold(s)).joinToString(" ") { it.value }
        return if (value == "zoos") "zoo" else value
    }
    // Port of candidate_data.py:candidates_for (L127–151).
    fun candidatesFor(destination: String, category: String, maxPrice: Double?, preferences: List<String>): List<TravelOption>? {
        val rows = loader.candidates(destination, category) ?: return null
        val wanted = preferences.map(::preferenceKey)
        return rows.map { json.decodeFromJsonElement<TravelOption>(it) }
            .filter { maxPrice == null || !pyFloatExceeds(it.price, maxPrice) }
            .map { option -> option.copy(preference_matches = wanted.filter { it in option.tags.map(::preferenceKey).toSet() }) }
            .sortedWith { a, b ->
                val matches = b.preference_matches.size.compareTo(a.preference_matches.size)
                if (matches != 0) matches else pyFloatCompare(a.price, b.price).let { if (it != 0) it else a.id.compareTo(b.id) }
            }
    }
    // Port of itinerary.py:select_option (L121–122).
    fun selectOption(options: List<TravelOption>, day: Int, cheap: Boolean): TravelOption =
        if (cheap) options.minWith { a, b -> pyFloatCompare(a.price, b.price).let { if (it != 0) it else a.id.compareTo(b.id) } }
        else options[(day - 1) % options.size]

    private data class Draft(val daily: List<DayPlan>, val costs: List<Pair<String, BigDecimal>>, val warnings: List<String>, val ids: Map<String, List<String>>)
    // Port of itinerary.py:compose_draft (L43–118).
    private fun compose(r: TravelRequirements, options: Map<String, List<TravelOption>>, days: Int, cheap: Boolean): Draft {
        val multiplier = BigDecimal.valueOf((r.travelers ?: 1).toLong())
        val ids = categories.associateWith { mutableListOf<String>() }
        val costs = mutableListOf<Pair<String, BigDecimal>>()
        val daily = (1..days).map { number ->
            val attraction = selectOption(options.getValue("attractions"), number, cheap)
            val food = selectOption(options.getValue("food"), number, cheap)
            val transport = selectOption(options.getValue("transport"), 1, cheap)
            val chosen = mutableListOf(Triple(attraction, "attractions", attraction.name))
            for (meal in listOf("breakfast", "lunch", "dinner")) chosen.add(Triple(food, "food", "${food.name} ($meal)"))
            chosen.add(Triple(transport, "transport", transport.name))
            if (number < days) { val hotel = selectOption(options.getValue("hotel"), 1, cheap); chosen.add(Triple(hotel, "hotel", hotel.name)) }
            val activities = chosen.map { (option, category, name) ->
                if (option.id !in ids.getValue(category)) ids.getValue(category).add(option.id)
                costs.add(category to option.price)
                Activity(name, category, money(option.price.multiply(multiplier, DECIMAL_CONTEXT)))
            }
            DayPlan(number, activities, money(activities.fold(ZERO) { sum, activity -> sum.add(activity.estimated_cost, DECIMAL_CONTEXT) }))
        }
        val warnings = mutableListOf("Offline mock estimates only; no live availability, tax, tips or intercity fares.", "Quote convention: three meals/day and days minus one lodging nights; prices per person.")
        if (days > options.getValue("attractions").size) warnings.add("Small mock dataset: some attractions repeat across days.")
        if (r.constraints.isNotEmpty()) warnings.add("Constraints are recorded but not verified by this mock planner: " + r.constraints.joinToString(", "))
        if (r.start_date != null || r.end_date != null) warnings.add("Dates determine duration only; opening hours and dated availability are not checked.")
        if (cheap) warnings.add("Budget repair selected the least-cost matching options for each day.")
        return Draft(daily, costs, warnings, ids)
    }
    // Port of mock.py:calculate_budget (L95–150).
    private fun budget(r: TravelRequirements, costs: List<Pair<String, BigDecimal>>): BudgetSummary {
        val breakdown = linkedMapOf("hotel" to ZERO, "food" to ZERO, "transport" to ZERO, "attractions" to ZERO)
        for ((category, amount) in costs) breakdown[category] = breakdown.getValue(category).add(amount, DECIMAL_CONTEXT)
        val perPerson = breakdown.values.fold(ZERO) { sum, amount -> sum.add(amount, DECIMAL_CONTEXT) }
        val multiplier = BigDecimal.valueOf((r.travelers ?: 1).toLong())
        val total = perPerson.multiply(multiplier, DECIMAL_CONTEXT)
        val warnings = mutableListOf<String>(); var within: Boolean? = null; var comparison: BigDecimal? = null
        if (r.travelers == null) warnings.add("Traveler count is unspecified: costs are per traveler, not a group total.")
        val limit = r.budget_amount
        if (limit != null) when {
            r.currency != "USD" -> warnings.add("Mock prices are USD; no currency conversion or budget comparison was made.")
            r.budget_scope == "UNKNOWN" -> warnings.add("Budget scope is unknown; no budget comparison was made.")
            r.budget_scope == "PER_PERSON" -> { comparison = perPerson; within = perPerson <= limit; if (!within) warnings.add("Estimated per-person cost exceeds the supplied budget.") }
            r.travelers == null -> { warnings.add("Group budget cannot be verified without a traveler count."); if (total > limit) warnings.add("Even the per-traveler reference cost exceeds the supplied budget.") }
            else -> { comparison = total; within = total <= limit; if (!within) warnings.add("Estimated group cost exceeds the supplied budget.") }
        }
        return BudgetSummary("USD", if (r.travelers == null) "per_traveler" else "group", r.travelers,
            money(perPerson), money(total), breakdown.mapValues { money(it.value.multiply(multiplier, DECIMAL_CONTEXT)) }, limit, r.currency,
            r.budget_scope, within, comparison?.let(::money), if (comparison != null && limit != null) money(limit.subtract(comparison, DECIMAL_CONTEXT)) else null, warnings)
    }
    // Port of itinerary.py:validate_itinerary (L125–174).
    private fun validate(r: TravelRequirements, trip: Itinerary, b: BudgetSummary): Pair<ValidationResult, List<String>> {
        val errors = mutableListOf<String>(); val violations = mutableListOf<Violation>()
        if (trip.daily_plan.map { it.day_number } != (1..trip.days).toList()) errors.add("Itinerary day numbering or duration is inconsistent")
        for (day in trip.daily_plan) {
            val subtotal = money(day.activities.fold(ZERO) { sum, activity -> sum.add(activity.estimated_cost, DECIMAL_CONTEXT) })
            if (!pyFloatEqual(subtotal, day.estimated_cost)) errors.add("Daily cost does not match activities")
        }
        val total = money(trip.daily_plan.fold(ZERO) { sum, day -> sum.add(day.estimated_cost, DECIMAL_CONTEXT) })
        if (!pyFloatEqual(total, b.estimated_total_cost) || !pyFloatEqual(total, trip.estimated_total_cost)) errors.add("Itinerary cost does not match budget calculation")
        if (b.basis == "group") for (constraint in r.requirements_v2.constraints) {
            if (constraint.currency != b.currency) continue
            val actual = money(if (constraint.scope == "TOTAL_TRIP") b.estimated_total_cost else b.breakdown.getValue("hotel"))
            val expected = money(constraint.value); val excess = money(actual.subtract(expected, DECIMAL_CONTEXT))
            if (pyFloatPositive(excess)) {
                val code = if (constraint.scope == "TOTAL_TRIP") "HARD_BUDGET_EXCEEDED" else "HOTEL_BUDGET_EXCEEDED"
                val message = if (constraint.scope == "TOTAL_TRIP") "Hard budget exceeded" else "Hotel budget exceeded"
                errors.add(message); violations.add(Violation(code, "budget", expected, actual, excess, message))
            }
        }
        return ValidationResult(true, if (errors.isEmpty()) "passed" else "failed", null, violations) to errors
    }
    private enum class Node { PREFLIGHT, CLARIFICATION, PLANNER, TOOLS, VALIDATION, REPLAN, FINALIZATION, END }
    private data class ReplanContext(val violations: List<Violation>, val previousItinerary: Itinerary, val attempt: Int)
    // Corresponds to state.py:TravelState; messages are represented by clarificationQuestion in this non-chat entry point.
    private class PipelineState(val requirements: TravelRequirements, val query: String) {
        val executionStages = mutableListOf<Stage>()
        var requirementStatus = "INSUFFICIENT"
        val missingFields = mutableListOf<String>()
        var days: Int? = null
        var toolRequests = emptyList<ToolRecord>()
        var approvedToolRequests = emptyList<ToolRecord>()
        var toolResults = linkedMapOf<String, List<TravelOption>>()
        var selectedCandidateIds: Map<String, List<String>> = emptyMap()
        var itinerary: Itinerary? = null
        var budgetSummary: BudgetSummary? = null
        var lastBudgetResult: BudgetSummary? = null
        var warnings = emptyList<String>()
        var errors = emptyList<String>()
        var clarificationQuestion: String? = null
        var validationStatus = "not_reached"
        var publicValidation = ValidationResult(false, "not_performed", "not_reached", emptyList())
        var replanAttempts = 0
        var replanContext: ReplanContext? = null
        var budgetRepair = false
        var candidateGroups = emptyList<CandidateGroup>()
        fun stage(name: String, outcome: String = "completed") { executionStages.add(Stage(name, executionStages.size + 1, outcome)) }
    }

    // Port of service.py:TravelService.run and graph.py:build_graph (L26–292), bypassing text parsing.
    fun runWithRequirements(r: TravelRequirements, query: String = ""): EngineRun {
        val state = PipelineState(r, query)
        var node = Node.PREFLIGHT
        while (node != Node.END) {
            node = when (node) {
                Node.PREFLIGHT -> {
                    if (r.destination.isNullOrEmpty()) state.missingFields.add("destination")
                    state.days = r.duration_days ?: if (r.start_date != null && r.end_date != null)
                        (ChronoUnit.DAYS.between(LocalDate.parse(r.start_date), LocalDate.parse(r.end_date)) + 1).toInt() else null
                    if (state.days == null) state.missingFields.add("duration")
                    state.requirementStatus = if (state.missingFields.isEmpty()) "SUFFICIENT" else "INSUFFICIENT"
                    state.stage("preflight", if (state.missingFields.isEmpty()) "completed" else "clarification")
                    if (state.missingFields.isEmpty()) Node.PLANNER else Node.CLARIFICATION
                }
                Node.CLARIFICATION -> {
                    state.clarificationQuestion = state.missingFields.joinToString(" ") {
                        if (it == "destination") "Where would you like to travel?" else "For how many days, or on which start and end dates?"
                    }
                    state.stage("clarification"); Node.END
                }
                Node.PLANNER -> {
                    val destination = r.destination!!; val days = state.days!!
                    val hotel = r.requirements_v2.constraints.firstOrNull { it.scope == "HOTEL_TOTAL" }
                    val maxPrice = if (hotel != null && hotel.currency == "USD" && r.travelers != null && days > 1)
                        pyFloatHotelCeiling(hotel.value, r.travelers, days - 1) else null
                    val preferences = listOf(r.interests.filter { it != "food" }, r.hotel_preferences, r.food_preferences, r.transport_preferences)
                    state.toolRequests = categories.mapIndexed { index, category ->
                        val arguments = SearchArguments(destination, if (category == "hotel") maxPrice else null, preferences[index])
                        ToolRecord(toolNames[index], arguments, arguments)
                    } + ToolRecord("calculate_budget", null, null)
                    state.approvedToolRequests = state.toolRequests.toList()
                    state.warnings = emptyList(); state.budgetRepair = state.replanContext != null
                    state.stage("planner"); Node.TOOLS
                }
                Node.TOOLS -> {
                    if (state.errors.isNotEmpty()) {
                        state.stage("tools", "skipped")
                    } else {
                        val results = categories.mapIndexed { index, category ->
                            val args = state.toolRequests[index].requestedArguments!!
                            candidatesFor(args.destination, category, args.maxPrice, args.preferences)
                        }
                        state.errors = if (results.any { it == null }) listOf("UNSUPPORTED_CITY_DATA") else
                            results.mapIndexedNotNull { index, result -> if (result!!.isEmpty()) "${toolNames[index]}: NO_RESULTS" else null }
                        state.toolResults = linkedMapOf()
                        results.forEachIndexed { index, result -> if (result != null) state.toolResults[categories[index]] = result }
                        state.candidateGroups = state.toolResults.mapNotNull { (category, rows) ->
                            if (rows.isEmpty()) null else CandidateGroup(category, emptyList(), rows)
                        }
                        if (state.errors.isEmpty()) {
                            val draft = compose(r, state.toolResults, state.days!!, state.budgetRepair)
                            val calculated = budget(r, draft.costs)
                            state.warnings = state.warnings + draft.warnings + calculated.warnings
                            state.itinerary = Itinerary(r.destination!!, state.days!!, "USD", calculated.estimated_total_cost, draft.daily, state.warnings)
                            state.budgetSummary = calculated; state.lastBudgetResult = calculated
                            state.selectedCandidateIds = draft.ids
                            state.candidateGroups = state.toolResults.map { (category, rows) ->
                                val selected = draft.ids.getValue(category).toSet()
                                CandidateGroup(category, rows.filter { it.id in selected }, rows.filter { it.id !in selected })
                            }
                        }
                        state.stage("tools", if (state.errors.isEmpty()) "completed" else "failed")
                    }
                    Node.VALIDATION
                }
                Node.VALIDATION -> {
                    if (state.errors.isNotEmpty()) {
                        state.validationStatus = "failed"
                        state.publicValidation = ValidationResult(false, "not_performed", "prior_errors", emptyList())
                        state.stage("validation", "skipped")
                    } else if (state.itinerary == null || state.budgetSummary == null) {
                        state.errors = listOf("Planning did not produce an itinerary and budget")
                        state.validationStatus = "failed"
                        state.publicValidation = ValidationResult(false, "not_performed", "missing_artifacts", emptyList())
                        state.stage("validation", "failed")
                    } else {
                        val checked = validate(r, state.itinerary!!, state.budgetSummary!!)
                        state.publicValidation = checked.first; state.errors = checked.second
                        state.validationStatus = checked.first.outcome
                        state.stage("validation", if (state.errors.isEmpty()) "completed" else "failed")
                    }
                    val v = state.publicValidation
                    val eligible = v.performed && v.outcome == "failed" && v.violations.isNotEmpty() &&
                        v.violations.all { it.code == "HARD_BUDGET_EXCEEDED" || it.code == "HOTEL_BUDGET_EXCEEDED" } &&
                        state.errors == v.violations.map { it.message }
                    if (state.replanAttempts < 1 && eligible) Node.REPLAN else Node.FINALIZATION
                }
                Node.REPLAN -> {
                    state.replanAttempts++
                    state.replanContext = ReplanContext(state.publicValidation.violations.toList(), state.itinerary!!, state.replanAttempts)
                    state.errors = emptyList(); state.budgetRepair = false
                    state.stage("replan"); Node.PLANNER
                }
                Node.FINALIZATION -> {
                    if (state.errors.isNotEmpty()) { state.itinerary = null; state.budgetSummary = null }
                    state.stage("finalization"); Node.END
                }
                Node.END -> Node.END
            }
        }
        val status = if (state.requirementStatus == "INSUFFICIENT") "needs_clarification" else if (state.errors.isEmpty()) "success" else "error"
        val previous = state.replanContext?.previousItinerary
        val finalBudget = state.budgetSummary ?: state.lastBudgetResult
        val repair = previous?.let { RepairSummary(state.replanAttempts, state.replanContext!!.violations, it, finalBudget, state.publicValidation.outcome) }
        val savings = if (previous != null && finalBudget != null) pyFloatSavings(previous.estimated_total_cost, finalBudget.estimated_total_cost) else null
        val result = PlanResult(query, r, state.candidateGroups, if (status == "success") state.itinerary else null,
            if (status == "success") state.budgetSummary else null, state.publicValidation, repair, status,
            state.missingFields, state.clarificationQuestion, state.warnings)
        return EngineRun(result, state.executionStages, state.approvedToolRequests, savings)
    }
}
