package travel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import travel.theme.*
import travel_agent_kmp.composeapp.generated.resources.*

@Composable fun WidePage(cities: Int, query: String, onQuery: (String) -> Unit, onRun: (String) -> Unit, run: EngineRun?, stage: Int, running: Boolean, failed: Boolean, onAbout: () -> Unit, onRepair: () -> Unit, onEdit: () -> Unit) {
    val c = LocalTravelColors.current; val f = LocalTravelFonts.current; var selected by remember { mutableIntStateOf(-1) }
    val names = listOf(stringResource(Res.string.request_stage), stringResource(Res.string.requirements_stage), stringResource(Res.string.candidates_stage), stringResource(Res.string.validation_stage), stringResource(Res.string.repair_stage))
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.app_name), Modifier.padding(top = 12.dp), color = c.text, fontFamily = f.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            BasicTextField(query, onQuery, Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp, c.inputBorder, RoundedCornerShape(14.dp)).padding(14.dp), singleLine = true, textStyle = TextStyle(color = c.text, fontFamily = f.body, fontSize = 16.sp, letterSpacing = 0.sp, fontFeatureSettings = "tnum"))
            Button(onClick = { selected = -1; onRun(query) }, enabled = query.isNotBlank(), modifier = Modifier.height(52.dp), shape = RoundedCornerShape(14.dp)) { Text(stringResource(Res.string.plan_trip)) }
            TextButton(onClick = onAbout) { Text(stringResource(Res.string.about)) }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.widthIn(min = 300.dp, max = 360.dp).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(18.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(Res.string.stages), color = c.text, fontFamily = f.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                names.forEachIndexed { index, label ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (selected == index) c.primaryContainer else c.surface).clickable { selected = index }.padding(10.dp)) {
                        Text("${index + 1}", Modifier.padding(end = 14.dp), color = if (index <= stage && run != null) c.primary else c.textMuted)
                        Column {
                            Text(label, color = c.text, fontWeight = FontWeight.SemiBold)
                            val unavailable = run == null || index > stage || index >= 2 && run.result.status == "needs_clarification"
                            val failed = run != null && (index == 3 && (run.result.repair?.trigger?.isNotEmpty() == true || run.result.validation.outcome == "failed") || index == 4 && run.result.repair?.final_outcome == "failed")
                            Text(if (unavailable) stringResource(Res.string.not_reached) else if (failed) stringResource(Res.string.failed) else stringResource(Res.string.passed), color = if (failed) c.error else c.textMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
            if (run == null) Box(Modifier.weight(1f).fillMaxHeight()) { Home(cities, query, onQuery, onRun, running, failed, onAbout) }
            else Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (selected >= 0 && selected < 5) {
                    Text(names[selected], color = c.text, fontFamily = f.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    when (selected) {
                        0 -> Text(run.result.query, color = c.text)
                        1 -> Text("${run.result.requirements.destination} · ${run.result.requirements.duration_days} · ${run.result.requirements.travelers}", color = c.text)
                        2 -> run.result.candidates.forEach { group -> Text("${categoryLabel(group.category)}: ${(group.selected + group.alternatives).joinToString { it.name }}", color = c.text) }
                        3 -> (run.result.repair?.trigger ?: run.result.validation.violations).forEach { Text("${it.code} · $${MoneyFormatting.format(it.actual)} / $${MoneyFormatting.format(it.expected)}", color = c.error) }
                        4 -> run.result.repair?.let { repair ->
                            Text(stringResource(Res.string.repair_description), color = c.onWarningContainer)
                            repair.initial_itinerary.daily_plan.forEachIndexed { index, old ->
                                val after = run.result.itinerary?.daily_plan?.getOrNull(index)
                                Text("${stringResource(Res.string.day)} ${old.day_number}: $${MoneyFormatting.format(old.estimated_cost)} → ${after?.let { "$${MoneyFormatting.format(it.estimated_cost)}" } ?: "—"}", color = c.text)
                            }
                            TextButton(onClick = onRepair) { Text(stringResource(Res.string.repair_detail)) }
                        }
                    }
                    TextButton(onClick = { selected = -1 }) { Text(stringResource(Res.string.overview)) }
                } else {
                    val budget = run.result.budget
                    if (run.result.status == "error") Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.errorContainer).padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(if (run.result.repair != null) Res.string.could_not_fit else Res.string.error), color = c.onErrorContainer, fontFamily = f.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        run.result.repair?.trigger?.forEach { violation -> Text("${violation.code} · $${MoneyFormatting.format(violation.actual)} / $${MoneyFormatting.format(violation.expected)}", color = c.onErrorContainer) }
                        TextButton(onClick = onEdit) { Text(stringResource(Res.string.edit_request)) }
                    }
                    if (budget != null) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.hero).padding(22.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (run.result.repair != null) stringResource(Res.string.passed_repair) else stringResource(Res.string.passed), color = c.heroAccent)
                            run.savings?.let { Text(stringResource(Res.string.saved, "$${MoneyFormatting.format(it)}"), color = c.heroWarm) }
                        }
                        Text("$${MoneyFormatting.format(budget.estimated_total_cost)}", color = c.onHero, fontFamily = f.title, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                        budget.provided_limit?.let { Text(stringResource(Res.string.of_budget, "$${MoneyFormatting.format(it)}"), color = c.onHeroMuted) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf("hotel", "food", "attractions").forEach { key -> Text("${categoryLabel(key)}\n$${MoneyFormatting.format(budget.breakdown[key] ?: java.math.BigDecimal.ZERO)}", color = c.onHeroMuted) }
                            budget.remaining_budget?.let { Text("${stringResource(Res.string.left_capital)}\n$${MoneyFormatting.format(it)}", color = c.onHeroMuted) }
                        }
                    }
                    run.result.itinerary?.daily_plan?.firstOrNull()?.let { day ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(Res.string.sketch), color = c.text, fontFamily = f.title, fontWeight = FontWeight.SemiBold)
                            Sketch(placesForDay(run, day))
                            Text(stringResource(Res.string.sketch_caption), color = c.textMuted, fontSize = 12.sp)
                        }
                    }
                    run.result.itinerary?.daily_plan?.chunked(3)?.forEach { days -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        days.forEach { day -> Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(Res.string.day_amount, day.day_number, "$${MoneyFormatting.format(day.estimated_cost)}"), color = c.text, fontFamily = f.title, fontWeight = FontWeight.SemiBold)
                            day.activities.forEach { Text("${it.name} · $${MoneyFormatting.format(it.estimated_cost)}", color = c.textMuted, fontSize = 13.sp) }
                        } }
                        repeat(3 - days.size) { Spacer(Modifier.weight(1f)) }
                    } }
                    run.result.warnings.forEach { Text(it, color = c.textMuted, fontSize = 13.sp) }
                    TextButton(onClick = onAbout) { Text(stringResource(Res.string.data_limits)) }
                }
            }
        }
    }
}
