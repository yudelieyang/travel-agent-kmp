package travel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import travel.theme.*
import travel_agent_kmp.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun RunPage(run: EngineRun, stage: Int, onBack: () -> Unit, onSkip: () -> Unit, onRepair: () -> Unit) {
    val c = LocalTravelColors.current; val f = LocalTravelFonts.current; val r = run.result
    val names = listOf(stringResource(Res.string.request_stage), stringResource(Res.string.requirements_stage), stringResource(Res.string.candidates_stage), stringResource(Res.string.validation_stage), stringResource(Res.string.repair_stage))
    var candidates by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            val titleDays = r.requirements.duration_days?.let { stringResource(Res.string.days, it) }
            Text(r.requirements.destination?.let { listOfNotNull(it, titleDays).joinToString(" · ") } ?: stringResource(Res.string.planning), Modifier.weight(1f), color = c.text, fontFamily = f.title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            TextButton(onClick = onSkip) { Text(stringResource(Res.string.skip)) }
        }
        Text(stringResource(Res.string.stage_progress, stage + 1, names[stage]), color = c.textMuted, fontFamily = f.body, fontSize = 13.sp)
        LinearProgressIndicator(progress = { (stage + 1) / 5f }, modifier = Modifier.fillMaxWidth().height(5.dp), color = c.primary, trackColor = c.border)
        Surface(shape = RoundedCornerShape(16.dp), color = c.surface, border = androidx.compose.foundation.BorderStroke(1.dp, c.border)) { Text("“${r.query}”", Modifier.padding(16.dp), color = c.textMuted, fontFamily = f.body, lineHeight = 20.sp) }
        names.forEachIndexed { index, name ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                val reached = when (index) { 2 -> run.stages.any { it.name == "tools" && it.outcome != "skipped" }; 3 -> r.validation.performed; 4 -> r.status != "needs_clarification"; else -> true }
                val stageFailed = index == 3 && (r.repair?.trigger?.isNotEmpty() == true || r.validation.outcome == "failed") || index == 4 && r.repair?.final_outcome == "failed"
                val dot = when { index > stage || !reached -> c.background; stageFailed -> c.error; index == 3 || index == 4 && r.validation.outcome == "passed" -> c.success; else -> c.primary }
                Column(Modifier.width(30.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(dot).border(1.dp, if (index <= stage && reached) dot else c.inputBorder, CircleShape).clearAndSetSemantics { testTag = "stage_${index}_${if (index > stage || !reached) "pending" else if (stageFailed) "failed" else "passed"}" }, contentAlignment = Alignment.Center) { Text(if (index > stage || !reached) "" else if (stageFailed) "!" else "✓", color = c.onPrimary) }
                    if (index < 4) Spacer(Modifier.weight(1f).width(2.dp).background(if (index < stage) c.primary else c.border))
                }
                Column(Modifier.weight(1f).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name, fontFamily = f.title, fontWeight = FontWeight.SemiBold, color = c.text, fontSize = 17.sp)
                    if (index <= stage && !reached) Text(stringResource(Res.string.not_reached), color = c.textMuted)
                    else if (index <= stage) when (index) {
                        0 -> Text(stringResource(Res.string.character_count, r.query.codePointCount(0, r.query.length)), color = c.textMuted, fontFamily = f.body)
                        1 -> {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOfNotNull(r.requirements.destination, r.requirements.duration_days?.let { stringResource(Res.string.days, it) }, r.requirements.travelers?.let { if (it == 1) stringResource(Res.string.one_traveler) else stringResource(Res.string.travelers, it) }).forEach { value ->
                                    Text(value, Modifier.clip(RoundedCornerShape(50)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp), color = c.text)
                                }
                            }
                            val budgetScope = when (r.requirements.budget_scope) { "TOTAL_TRIP" -> stringResource(Res.string.total_trip); "PER_PERSON" -> stringResource(Res.string.per_person); else -> "" }
                            r.requirements.budget_amount?.let { Text("≤ $${legendMoney(it)} $budgetScope · ${r.requirements.budget_constraint_strength}", Modifier.clip(RoundedCornerShape(50)).background(c.hero).padding(horizontal = 12.dp, vertical = 8.dp), color = c.onHero) }
                            r.requirements.specific_preferences.forEach { Text(it.value, Modifier.clip(RoundedCornerShape(50)).background(c.primaryContainer).padding(horizontal = 12.dp, vertical = 7.dp), color = c.primary) }
                            r.requirements.requirements_v2.ambiguities.forEach { Text(it.reason, Modifier.clip(RoundedCornerShape(50)).background(c.warningContainer).padding(horizontal = 12.dp, vertical = 7.dp), color = c.onWarningContainer) }
                        }
                        2 -> {
                            Text(stringResource(Res.string.candidate_description), color = c.textMuted)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                listOf("attractions" to stringResource(Res.string.sights), "hotel" to stringResource(Res.string.hotels), "food" to stringResource(Res.string.food), "transport" to stringResource(Res.string.transit)).forEach { (key, label) ->
                                    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(10.dp)).padding(7.dp)) {
                                        Text("${r.candidates.find { it.category == key }?.let { it.selected.size + it.alternatives.size } ?: 0}", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Text(label, color = c.textMuted, fontSize = 11.sp)
                                    }
                                }
                            }
                            Text(stringResource(Res.string.view_candidates), Modifier.clickable { candidates = true }.padding(vertical = 12.dp), color = c.primary, fontWeight = FontWeight.SemiBold)
                        }
                        3 -> { val violation = r.repair?.trigger?.firstOrNull() ?: r.validation.violations.firstOrNull(); Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (violation == null) c.successContainer else c.errorContainer).clickable(enabled = r.repair != null, onClick = onRepair).padding(14.dp)) { if (violation == null) Text(stringResource(Res.string.validation_passed), color = c.onSuccessContainer) else { Text(violation.code, color = c.onErrorContainer, fontFamily = f.code, fontSize = 11.sp); Text(stringResource(Res.string.validation_summary, "$${MoneyFormatting.format(violation.actual)}", "$${MoneyFormatting.format(violation.excess)}", "$${MoneyFormatting.format(violation.expected)}"), color = c.onErrorContainer) } } }
                        4 -> { Text(r.repair?.final_outcome ?: r.validation.outcome, color = c.textMuted); if (r.repair != null) TextButton(onClick = onRepair) { Text(stringResource(Res.string.repair_detail)) } }
                    }
                }
            }
        }
    }
    if (candidates) ModalBottomSheet(onDismissRequest = { candidates = false }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).verticalScroll(rememberScrollState()).padding(20.dp)) {
            r.candidates.forEach { group ->
                Text(categoryLabel(group.category), fontFamily = f.title, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                listOf(stringResource(Res.string.selected) to group.selected, stringResource(Res.string.alternatives) to group.alternatives).forEach { (label, options) ->
                    Text(label, color = c.textMuted, fontSize = 12.sp)
                    options.forEach { option -> Text("${option.name} · $${MoneyFormatting.format(option.price)} / ${option.unit}\n${(option.tags + option.preference_matches + listOfNotNull(option.source)).joinToString(" · ")}", Modifier.padding(vertical = 8.dp), color = c.text, fontSize = 14.sp) }
                }
            }
        }
    }
}
