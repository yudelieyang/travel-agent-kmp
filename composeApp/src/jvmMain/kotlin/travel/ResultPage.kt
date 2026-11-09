package travel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import travel.theme.*
import travel_agent_kmp.composeapp.generated.resources.*
import kotlin.math.cos

@Composable fun ResultPage(run: EngineRun, onBack: () -> Unit, onEdit: () -> Unit, onStages: () -> Unit, onRepair: () -> Unit) {
    val c = LocalTravelColors.current; val f = LocalTravelFonts.current; val result = run.result
    val trip = result.itinerary; val budget = result.budget
    var dayIndex by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth()) { val backDescription = stringResource(Res.string.cancel); TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = backDescription }) { Text("←") }; Text(result.requirements.destination ?: stringResource(Res.string.result_title), Modifier.weight(1f).padding(12.dp), color = c.text, fontFamily = f.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold); TextButton(onClick = onStages) { Text(stringResource(Res.string.stages)) } }
        if (trip == null || budget == null) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.errorContainer).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (result.repair != null) Res.string.could_not_fit else Res.string.error), fontFamily = f.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.onErrorContainer)
                result.repair?.trigger?.forEach { Text("${it.code} · $${MoneyFormatting.format(it.actual)} / $${MoneyFormatting.format(it.expected)}", color = c.onErrorContainer) }
                TextButton(onClick = onEdit) { Text(stringResource(Res.string.edit_request)) }
            }
        } else {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.hero).border(1.dp, c.border, RoundedCornerShape(20.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (result.repair != null) stringResource(Res.string.passed_repair) else stringResource(Res.string.passed), color = c.heroAccent, fontWeight = FontWeight.SemiBold)
                    run.savings?.let { Text(stringResource(Res.string.saved, "$${MoneyFormatting.format(it)}"), color = c.heroWarm) }
                }
                Text("$${MoneyFormatting.format(budget.estimated_total_cost)}", color = c.onHero, fontFamily = f.title, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                budget.provided_limit?.let { limit -> Text(stringResource(Res.string.of_budget, "$${MoneyFormatting.format(limit)}") + (budget.remaining_budget?.let { " · $${MoneyFormatting.format(it)} ${stringResource(Res.string.left)}" } ?: ""), color = c.onHeroMuted) }
                val total = (budget.provided_limit ?: budget.estimated_total_cost).toFloat().coerceAtLeast(0.01f)
                BoxWithConstraints(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(c.border)) {
                    val barWidth = maxWidth
                    Row(Modifier.fillMaxSize()) {
                        listOf("hotel" to c.heroAccent, "food" to c.heroWarm, "attractions" to c.onHero, "transport" to c.primary).forEach { (key, color) ->
                            val fraction = ((budget.breakdown[key]?.toFloat() ?: 0f) / total).coerceIn(0f, 1f)
                            if (fraction > 0f) Spacer(Modifier.width(barWidth * fraction).fillMaxHeight().background(color))
                        }
                    }
                }
                val legendColors = mapOf("hotel" to c.heroAccent, "food" to c.heroWarm, "attractions" to c.onHero, "transport" to c.primary)
                budget.breakdown.forEach { (key, value) -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (value.signum() != 0) Spacer(Modifier.padding(top = 4.dp).size(8.dp).clip(RoundedCornerShape(50)).background(legendColors.getValue(key)))
                    Text("${categoryLabel(key)} · $${MoneyFormatting.format(value)}", color = c.onHeroMuted, fontSize = 12.sp)
                } }
                if (result.repair != null) TextButton(onClick = onRepair) { Text(stringResource(Res.string.repair_detail), color = c.heroAccent) }
            }
            val day = trip.daily_plan.getOrNull(dayIndex) ?: trip.daily_plan.first()
            val selected = result.candidates.flatMap { it.selected }.filter { it.latitude != null && it.longitude != null }
            val places = day.activities.mapNotNull { a -> selected.firstOrNull { a.name == it.name || a.name.startsWith(it.name + " (") } }.distinctBy { it.id }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.sketch), color = c.text, fontFamily = f.title, fontWeight = FontWeight.SemiBold)
                Sketch(places)
                Text(stringResource(Res.string.sketch_caption), color = c.textMuted, fontSize = 12.sp)
            }
            Row((if (trip.daily_plan.size > 3) Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth()).clip(RoundedCornerShape(50)).background(c.segmentTrack).padding(4.dp)) {
                trip.daily_plan.forEachIndexed { index, item -> Text(stringResource(Res.string.day_amount, item.day_number, "$${MoneyFormatting.format(item.estimated_cost)}"), (if (trip.daily_plan.size <= 3) Modifier.weight(1f) else Modifier).clip(RoundedCornerShape(50)).background(if (index == dayIndex) c.surface else c.segmentTrack).clickable { dayIndex = index }.padding(horizontal = 5.dp, vertical = 12.dp), color = c.text, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            day.activities.forEach { activity ->
                val meal = Regex("\\((breakfast|lunch|dinner)\\)$").find(activity.name)?.groupValues?.get(1)
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(categoryLabel(meal ?: activity.category).uppercase(), Modifier.width(90.dp), color = c.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(activity.name.substringBefore(" ("), Modifier.weight(1f), color = c.text, fontSize = 14.sp)
                    Text("$${MoneyFormatting.format(activity.estimated_cost)}", color = c.text, fontSize = 14.sp)
                }
            }
            if (result.warnings.isNotEmpty()) { Text(stringResource(Res.string.warnings), color = c.text, fontFamily = f.title, fontWeight = FontWeight.SemiBold); result.warnings.forEach { Text(it, color = c.textMuted, fontSize = 13.sp) } }
        }
    }
}

@Composable private fun Sketch(points: List<TravelOption>) {
    val c = LocalTravelColors.current
    val description = points.joinToString(", ") { it.name }
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    var selected by remember(points) { mutableStateOf<String?>(null) }
    val padPixels = with(LocalDensity.current) { 24.dp.toPx() }
    fun positions(width: Float, height: Float): List<Offset> {
        if (points.isEmpty()) return emptyList()
        val lat = points.map { it.latitude!! }.average()
        val projected = points.map { (it.longitude!! * cos(Math.toRadians(lat))) to -it.latitude!! }
        val minX = projected.minOf { it.first }; val maxX = projected.maxOf { it.first }; val minY = projected.minOf { it.second }; val maxY = projected.maxOf { it.second }
        val scale = minOf((width - 2 * padPixels) / (maxX - minX).coerceAtLeast(.00001), (height - 2 * padPixels) / (maxY - minY).coerceAtLeast(.00001)).toFloat()
        val x0 = (width - (maxX - minX).toFloat() * scale) / 2; val y0 = (height - (maxY - minY).toFloat() * scale) / 2
        return projected.map { Offset(x0 + (it.first - minX).toFloat() * scale, y0 + (it.second - minY).toFloat() * scale) }
    }
    Column {
    Box {
    Canvas(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp)).background(c.mapGround).onSizeChanged { bounds = it }.pointerInput(points, bounds) {
        detectTapGestures { tap -> val locations = positions(bounds.width.toFloat(), bounds.height.toFloat()); val closest = locations.indices.minByOrNull { (locations[it] - tap).getDistance() }; selected = closest?.takeIf { (locations[it] - tap).getDistance() < padPixels }?.let { points[it].name } }
    }.semantics { contentDescription = description }) {
        if (points.isNotEmpty()) {
            val positions = positions(size.width, size.height)
            positions.zipWithNext().forEach { (a,b) -> drawLine(c.primary, a, b, 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))) }
            positions.forEachIndexed { index, at -> val color = when (points[index].category) { "hotel" -> c.primary; "food" -> c.warning; else -> c.text }; drawCircle(if (points[index].category == "hotel") color else c.surface, 6.dp.toPx(), at); drawCircle(color, 6.dp.toPx(), at, style = Stroke(2.dp.toPx())) }
        }
    }
    val labels = mutableListOf<androidx.compose.ui.geometry.Rect>()
    positions(bounds.width.toFloat(), bounds.height.toFloat()).forEachIndexed { index, point ->
        val width = (points[index].name.length * padPixels * .22f).coerceAtMost(bounds.width * .55f)
        val height = padPixels * .7f
        val x = (point.x + padPixels * .3f).coerceIn(0f, (bounds.width - width).coerceAtLeast(0f))
        val y = (point.y - height - padPixels * .2f).coerceIn(0f, (bounds.height - height).coerceAtLeast(0f))
        val rect = androidx.compose.ui.geometry.Rect(x, y, x + width, y + height)
        if (bounds.width > 0 && labels.none { it.overlaps(rect) }) {
            labels.add(rect)
            Text(points[index].name, Modifier.offset { IntOffset(x.toInt(), y.toInt()) }.background(c.mapGround), color = c.text, fontSize = 10.sp, maxLines = 1)
        }
    }
    }
    selected?.let { Text(it, color = c.text, fontSize = 13.sp) }
    }
}
