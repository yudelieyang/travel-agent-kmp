package travel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import travel.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun FailurePage(run: EngineRun, onBack: () -> Unit, onRun: (String) -> Unit) {
    val r = run.result; val c = LocalTravelColors.current; val f = LocalTravelFonts.current
    val destination = r.requirements.destination.orEmpty()
    val unknown = "UNSUPPORTED_CITY_DATA" in run.errors
    val noResults = run.errors.any { it.endsWith(": NO_RESULTS") }
    val budget = run.errors.any { it == "Hard budget exceeded" || it == "Hotel budget exceeded" }
    var query by remember(r.query) { mutableStateOf(r.query) }
    var showAll by remember { mutableStateOf(false) }
    val title = when { unknown -> "We couldn't find “$destination”"; noResults -> "No matching options found"; budget -> "Couldn't fit the budget"; else -> "Something in this request couldn't be planned offline" }
    val explanation = when { unknown -> "This offline build covers 12 US cities, and “$destination” isn't one of them — so there are no hotels, food or sights to choose from. Check the spelling, or pick a city below."; noResults -> "The offline dataset has no options matching the request."; budget -> "The least-cost repair still exceeds the budget."; else -> "An internal planning check did not complete." }
    val correction = when { noResults -> "Relax the budget or other constraints and run again."; budget -> "Increase the budget or shorten the trip and run again."; else -> "Edit the request and run again." }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row { BackButton(onBack); Text("Can't plan this yet", Modifier.padding(12.dp), color = c.text, fontFamily = f.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (budget || (!unknown && !noResults)) c.errorContainer else c.warningContainer).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val ink = if (budget || (!unknown && !noResults)) c.onErrorContainer else c.onWarningContainer
            Text(if (unknown) "UNKNOWN DESTINATION" else if (noResults) "NO MATCHING OPTIONS" else if (budget) "BUDGET" else "PLANNING ERROR", color = ink, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            Text(title, color = ink, fontFamily = f.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(explanation, color = ink)
        }
        Text("Understood so far", color = c.text, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOfNotNull(r.requirements.destination?.let { if (unknown) "City · $it — not found" else "City · $it" }, r.requirements.duration_days?.let { "Days · $it" }, r.requirements.travelers?.let { "Travelers · $it" }, r.requirements.budget_amount?.let { "Budget · $${MoneyFormatting.format(it)}" }, r.requirements.interests.takeIf { it.isNotEmpty() }?.let { "Interests · ${it.joinToString()}" }).forEach { Text(it, Modifier.clip(RoundedCornerShape(50)).background(if (unknown && it.startsWith("City ·")) c.warningContainer else c.primaryContainer).padding(10.dp), color = c.text) }
        }
        Text("How to fix it", color = c.text, fontWeight = FontWeight.Bold)
        Text(if (unknown) "Check the spelling or choose a city to replace it." else correction, color = c.textMuted)
        if (unknown) {
            val choices = if (showAll) CitySuggestions.all else CitySuggestions.closest(destination).ifEmpty { CitySuggestions.all.take(3) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { choices.forEach { city -> OutlinedButton(onClick = { onRun(query.replaceFirst(Regex(Regex.escape(destination), RegexOption.IGNORE_CASE), city)) }) { Text(city) } } }
            if (!showAll) TextButton(onClick = { showAll = true }) { Text("Show all 12 cities") }
        }
        BasicTextField(query, { query = it }, Modifier.fillMaxWidth().heightIn(min = 100.dp).clip(RoundedCornerShape(12.dp)).background(c.surface).border(1.dp, c.inputBorder, RoundedCornerShape(12.dp)).padding(14.dp), textStyle = TextStyle(color = c.text, fontFamily = f.body, fontSize = 16.sp))
        Button(onClick = { onRun(query) }, enabled = query.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Run again") }
        Text("Engine details: ${r.status}" + if (run.errors.isEmpty()) "" else " · ${run.errors.joinToString(" · ")}", color = c.textMuted, fontFamily = f.code, fontSize = 12.sp)
    }
}

