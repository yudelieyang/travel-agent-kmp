package travel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import travel.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun PreviewContent(preview: InputPreview, onCity: (String) -> Unit, onAllCities: () -> Unit) {
    val c = LocalTravelColors.current
    val req = preview.requirements
    val unknown = preview.unknownCity
    val interests = (req.interests + req.food_preferences + req.hotel_preferences + req.transport_preferences + req.specific_preferences.map { it.value }).distinct()
    Text("Understood so far", color = c.text, fontWeight = FontWeight.SemiBold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        @Composable fun Chip(label: String, state: Int) {
            val shape = RoundedCornerShape(50)
            val bg = if (state == 2) c.warningContainer else if (state == 1) c.successContainer else c.surface
            val border = if (state == 0) Modifier.drawBehind { drawRoundRect(color = c.inputBorder, cornerRadius = CornerRadius(size.height / 2), style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))) } else Modifier
            Text(label, Modifier.clip(shape).background(bg).then(border).padding(horizontal = 10.dp, vertical = 7.dp), color = if (state == 2) c.onWarningContainer else if (state == 1) c.onSuccessContainer else c.textMuted, fontSize = 12.sp)
        }
        Chip(if (req.destination == null) "City · needed" else if (unknown) "City: ${req.destination} — not found" else "✓ City · ${req.destination}", if (unknown) 2 else if (req.destination == null) 0 else 1)
        val duration = req.duration_days?.let { "$it days" } ?: if (req.start_date != null && req.end_date != null) "${req.start_date} – ${req.end_date}" else null
        Chip(if (duration == null) "Days · needed" else "✓ Days · $duration", if (duration == null) 0 else 1)
        Chip(req.travelers?.let { "✓ Travelers · $it" } ?: "Travelers — optional", if (req.travelers == null) 0 else 1)
        Chip(req.budget_amount?.let { "✓ Budget · ${req.currency} ${MoneyFormatting.format(it)}" } ?: "Budget — optional", if (req.budget_amount == null) 0 else 1)
        Chip(if (interests.isEmpty()) "Interests — optional" else "✓ Interests · ${interests.joinToString()}", if (interests.isEmpty()) 0 else 1)
    }
    if (unknown) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.warningContainer).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("“${req.destination}” isn't one of the 12 cities in the offline dataset. Tap one to replace it:", color = c.onWarningContainer, fontSize = 13.sp)
            val suggestions = CitySuggestions.closest(req.destination!!).ifEmpty { CitySuggestions.all.take(3) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { city -> OutlinedButton(onClick = { onCity(city) }) { Text(city) } }
                TextButton(onClick = onAllCities) { Text("All 12 →") }
            }
        }
    }
    if (preview.coverage.needs_llm || preview.coverage.reasons.isNotEmpty()) {
        val evidence = preview.coverage.evidence.joinToString("; ") { "“$it”" }
        Text("Some details may be ignored offline${if (evidence.isNotEmpty()) ": $evidence" else "."}", color = c.warning, fontSize = 12.sp)
    }
}
