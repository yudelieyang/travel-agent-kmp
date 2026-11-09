package travel

import java.math.BigDecimal
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

object MoneySerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Money", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): BigDecimal {
        val primitive = (decoder as JsonDecoder).decodeJsonElement() as JsonPrimitive
        return BigDecimal(primitive.content)
    }
    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())
}
typealias Money = @Serializable(with = MoneySerializer::class) BigDecimal

@Serializable data class SpecificPreference(val category: String, val value: String)
@Serializable data class RequirementAmbiguity(val level: String, val reason: String, val source_text: String)
@Serializable data class BudgetConstraint(val confidence: Double, val currency: String, val extractor_source: String, val kind: String, val operator: String, val scope: String, val source_text: String?, val strength: String, val value: Money)
@Serializable data class RequirementsV2(val ambiguities: List<RequirementAmbiguity>, val constraints: List<BudgetConstraint>, val objectives: List<String>, val preferences: List<SpecificPreference>)
@Serializable data class TravelRequirements(val budget_amount: Money?, val budget_constraint_strength: String, val budget_scope: String, val constraints: List<String>, val currency: String, val destination: String?, val duration_days: Int?, val end_date: String?, val food_preferences: List<String>, val hotel_preferences: List<String>, val interests: List<String>, val objective: String?, val origin: String?, val requirements_v2: RequirementsV2, val specific_preferences: List<SpecificPreference>, val start_date: String?, val transport_preferences: List<String>, val travelers: Int?)
@Serializable data class TravelOption(val id: String, val name: String, val destination: String, val city: String?, val state: String?, val category: String?, val price: Money, val currency: String, val unit: String, val tags: List<String>, val rating: Double?, val review_count: Int?, val image_url: String?, val source: String?, val description: String?, val preference_matches: List<String>, val address: String? = null, val latitude: Double? = null, val longitude: Double? = null, val provider: String? = null, val provider_place_id: String? = null, val provider_category_ids: List<String>? = null, val provider_category_labels: List<String>? = null, val provider_refreshed_at: String? = null, val snapshot_version: String? = null, val snapshot_fetched_at: String? = null, val cost_origin: String? = null, val cost_method: String? = null, val cost_version: String? = null)
@Serializable data class Activity(val name: String, val category: String, val estimated_cost: Money)
@Serializable data class DayPlan(val day_number: Int, val activities: List<Activity>, val estimated_cost: Money)
@Serializable data class Itinerary(val destination: String, val days: Int, val currency: String, val estimated_total_cost: Money, val daily_plan: List<DayPlan>, val warnings: List<String>)
@Serializable data class BudgetSummary(val currency: String, val basis: String, val travelers: Int?, val per_traveler_cost: Money, val estimated_total_cost: Money, val breakdown: Map<String, Money>, val provided_limit: Money?, val limit_currency: String, val budget_scope: String, val within_budget: Boolean?, val comparison_cost: Money?, val remaining_budget: Money?, val warnings: List<String>)
@Serializable data class Violation(val code: String, val category: String, val expected: Money, val actual: Money, val excess: Money, val message: String)
@Serializable data class ValidationResult(val performed: Boolean, val outcome: String, val reason: String?, val violations: List<Violation>)
@Serializable data class RepairSummary(val attempt: Int, val trigger: List<Violation>, val initial_itinerary: Itinerary, val final_budget: BudgetSummary?, val final_outcome: String)
@Serializable data class CandidateGroup(val category: String, val selected: List<TravelOption>, val alternatives: List<TravelOption>)
@Serializable data class PlanResult(val query: String, val requirements: TravelRequirements, val candidates: List<CandidateGroup>, val itinerary: Itinerary?, val budget: BudgetSummary?, val validation: ValidationResult, val repair: RepairSummary?, val status: String, val missing_fields: List<String>, val clarification_question: String?, val warnings: List<String>)
@Serializable data class CoverageSignals(val blocking_ambiguities: Int, val monetary_expressions: Int, val resolved_constraints: Int)
@Serializable data class CoverageResult(val needs_llm: Boolean, val reasons: List<String>, val evidence: List<String>, val signals: CoverageSignals)
@Serializable data class RequirementsFixture(val id: String, val query: String, val requirements: TravelRequirements, val coverage: CoverageResult)
