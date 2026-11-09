package travel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import travel.theme.*
import travel_agent_kmp.composeapp.generated.resources.*

@Composable fun RepairPage(run: EngineRun, onBack: () -> Unit) {
    val c = LocalTravelColors.current; val f = LocalTravelFonts.current
    val repair = run.result.repair ?: return
    val after = run.result.itinerary
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row { val backDescription = stringResource(Res.string.cancel); TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = backDescription }) { Text("←") }; Text(stringResource(Res.string.repair_title), Modifier.padding(12.dp), color = c.text, fontFamily = f.title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp) }
        repair.trigger.forEach { v ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.errorContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${v.code} · ${v.category}", fontFamily = f.code, color = c.onErrorContainer)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(stringResource(Res.string.draft) to v.actual, stringResource(Res.string.cap) to v.expected, stringResource(Res.string.over) to v.excess).forEach { (label, value) -> Column { Text(label, color = c.onErrorContainer); Text("$${MoneyFormatting.format(value)}", color = c.onErrorContainer, fontWeight = FontWeight.Bold) } }
                }
            }
        }
        Text("↻  ${stringResource(Res.string.repair_description)}", color = c.onWarningContainer, fontFamily = f.body)
        Text(stringResource(Res.string.before_after), fontFamily = f.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.text)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(stringResource(Res.string.day), color = c.textMuted); Text(stringResource(Res.string.before), color = c.textMuted); Text(stringResource(Res.string.after), color = c.textMuted) }
        repair.initial_itinerary.daily_plan.forEachIndexed { index, day ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${day.day_number}", color = c.text)
                Text("$${MoneyFormatting.format(day.estimated_cost)}", color = c.error)
                Text(after?.daily_plan?.getOrNull(index)?.let { "$${MoneyFormatting.format(it.estimated_cost)}" } ?: "—", color = c.success)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(Res.string.total), color = c.text, fontWeight = FontWeight.Bold)
            Text("$${MoneyFormatting.format(repair.initial_itinerary.estimated_total_cost)}", color = c.error)
            Text(repair.final_budget?.estimated_total_cost?.let { "$${MoneyFormatting.format(it)}" } ?: "—", color = c.success)
        }
        Text(stringResource(Res.string.changes), color = c.text, fontFamily = f.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        repair.initial_itinerary.daily_plan.forEachIndexed { index, old ->
            val updated = after?.daily_plan?.getOrNull(index) ?: return@forEachIndexed
            old.activities.zip(updated.activities).filter { (a, b) -> a.name != b.name }.distinctBy { it.first.category }.forEach { (a, b) ->
                Text("${stringResource(Res.string.day)} ${old.day_number} · ${if (a.category == "food") stringResource(Res.string.meals) else categoryLabel(a.category)}", color = c.textMuted)
                val multiplier = if (a.category == "food") 3.toBigDecimal() else java.math.BigDecimal.ONE
                Text("${a.name.substringBefore(" (")} · $${MoneyFormatting.format(a.estimated_cost.multiply(multiplier))}", color = c.textMuted, textDecoration = TextDecoration.LineThrough)
                Text("${b.name.substringBefore(" (")} · $${MoneyFormatting.format(b.estimated_cost.multiply(multiplier))}", color = c.text)
            }
        }
        val final = repair.final_budget
        val comparison = final?.provided_limit?.let { " ≤ $${MoneyFormatting.format(it)}" } ?: ""
        val remaining = final?.remaining_budget?.let { " · $${MoneyFormatting.format(it)} ${stringResource(Res.string.left)}" } ?: ""
        Text("${stringResource(Res.string.revalidated)}: ${final?.estimated_total_cost?.let { "$${MoneyFormatting.format(it)}" } ?: "—"}$comparison$remaining", Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (repair.final_outcome == "passed") c.successContainer else c.errorContainer).padding(16.dp), color = if (repair.final_outcome == "passed") c.onSuccessContainer else c.onErrorContainer)
    }
}
