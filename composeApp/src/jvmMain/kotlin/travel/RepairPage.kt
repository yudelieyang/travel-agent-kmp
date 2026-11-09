package travel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import travel.theme.*
import travel_agent_kmp.composeapp.generated.resources.*

@Composable fun RepairPage(run: EngineRun, onBack: () -> Unit) {
    val c = LocalTravelColors.current; val f = LocalTravelFonts.current
    val repair = run.result.repair ?: return
    val after = run.result.itinerary
    val passed = repair.final_outcome == "passed" && after != null
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row { BackButton(onBack); Text(stringResource(Res.string.repair_title), Modifier.padding(12.dp), color = c.text, fontFamily = f.title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp) }
        repair.trigger.forEach { violation ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.errorContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${violation.code} · ${if (violation.category == "budget") stringResource(Res.string.budget_label) else categoryLabel(violation.category)}", fontFamily = f.code, color = c.onErrorContainer)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(stringResource(Res.string.draft) to violation.actual, stringResource(Res.string.cap) to violation.expected, stringResource(Res.string.over) to violation.excess).forEachIndexed { index, (label, value) ->
                        Column { Text(label, color = c.onErrorContainer); Text("${if (index == 2) "+" else ""}$${MoneyFormatting.format(value)}", color = if (index == 2) c.error else c.onErrorContainer, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        if (passed) {
            val description = stringResource(Res.string.repair_description)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("↻", color = c.warning)
                Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(description.substringBefore(" · ")) }; if (" · " in description) append(" · " + description.substringAfter(" · ")) }, color = c.textMuted, fontFamily = f.body)
            }
        }
        Text(stringResource(Res.string.before_after), fontFamily = f.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.text)
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.day), Modifier.weight(1f), color = c.textMuted)
            Text(stringResource(Res.string.before), Modifier.weight(1f), color = c.textMuted, textAlign = TextAlign.End)
            if (passed) Text(stringResource(Res.string.after), Modifier.weight(1f), color = c.textMuted, textAlign = TextAlign.End)
        }
        repair.initial_itinerary.daily_plan.forEachIndexed { index, day ->
            Row(Modifier.fillMaxWidth()) {
                Text("${stringResource(Res.string.day)} ${day.day_number}", Modifier.weight(1f), color = c.text)
                Text("$${MoneyFormatting.format(day.estimated_cost)}", Modifier.weight(1f), color = c.textMuted, textAlign = TextAlign.End)
                if (passed) Text("$${MoneyFormatting.format(after!!.daily_plan[index].estimated_cost)}", Modifier.weight(1f), color = c.text, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.total), Modifier.weight(1f), color = c.text, fontWeight = FontWeight.Bold)
            Text("$${MoneyFormatting.format(repair.initial_itinerary.estimated_total_cost)}", Modifier.weight(1f), color = c.error, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            if (passed) Text(repair.final_budget?.estimated_total_cost?.let { "$${MoneyFormatting.format(it)}" } ?: "—", Modifier.weight(1f), color = c.success, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        }
        if (passed) {
            Text(stringResource(Res.string.changes), color = c.text, fontFamily = f.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            repair.initial_itinerary.daily_plan.forEachIndexed { index, old ->
                val updated = after!!.daily_plan[index]
                old.activities.zip(updated.activities).filter { (a, b) -> a.name != b.name }.distinctBy { it.first.category }.forEach { (a, b) ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${stringResource(Res.string.day)} ${old.day_number} · ${if (a.category == "food") stringResource(Res.string.meals) else categoryLabel(a.category)}", color = c.textMuted)
                        val each = if (a.category == "food") " ${stringResource(Res.string.each)}" else ""
                        Text("${a.name.substringBefore(" (")} · $${MoneyFormatting.format(a.estimated_cost)}$each", color = c.textMuted, textDecoration = TextDecoration.LineThrough)
                        Text("${b.name.substringBefore(" (")} · $${MoneyFormatting.format(b.estimated_cost)}$each", color = c.text)
                    }
                }
            }
        }
        val final = repair.final_budget
        val comparison = final?.provided_limit?.let { " ≤ $${MoneyFormatting.format(it)}" } ?: ""
        val remaining = final?.remaining_budget?.let { " · $${MoneyFormatting.format(it)} ${stringResource(Res.string.left)}" } ?: ""
        val summary = if (passed) "${stringResource(Res.string.revalidated)}: ${final?.estimated_total_cost?.let { "$${MoneyFormatting.format(it)}" } ?: "—"}$comparison$remaining" else stringResource(Res.string.validation_failed)
        Text(summary, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (passed) c.successContainer else c.errorContainer).padding(16.dp), color = if (passed) c.onSuccessContainer else c.onErrorContainer)
    }
}
