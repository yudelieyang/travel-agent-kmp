package travel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.*
import org.jetbrains.compose.resources.stringResource
import travel.theme.*
import travel_agent_kmp.composeapp.generated.resources.*

private data class SnapshotDate(val city: String, val category: String, val date: String)
private fun snapshotDates(): List<SnapshotDate> {
    val loader = requireNotNull(SnapshotLoader::class.java.classLoader)
    val root = "data/travel/snapshots/openstreetmap/"
    fun read(path: String) = requireNotNull(loader.getResourceAsStream(path)).bufferedReader().use { it.readText() }
    val manifest = Json.parseToJsonElement(read(root + "migration_manifest.json")).jsonObject
    return manifest["routes"]!!.jsonArray.map { row ->
        val route = row.jsonObject
        val data = Json.parseToJsonElement(read(root + route["snapshot"]!!.jsonPrimitive.content)).jsonObject
        SnapshotDate(route["city"]!!.jsonPrimitive.content, route["category"]!!.jsonPrimitive.content, data["snapshot_fetched_at"]!!.jsonPrimitive.content.substringBefore('T'))
    }
}

@Composable fun AboutPage(onBack: () -> Unit) {
    val c = LocalTravelColors.current; val f = LocalTravelFonts.current
    val dates = remember { snapshotDates() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row { BackButton(onBack); Text(stringResource(Res.string.about_title), Modifier.padding(12.dp), color = c.text, fontFamily = f.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(Res.string.osm_notice), color = c.text, fontFamily = f.body)
            Text(stringResource(Res.string.font_notice), color = c.textMuted, fontFamily = f.body, fontSize = 13.sp)
        }
        Text(stringResource(Res.string.limits), color = c.text, fontFamily = f.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        listOf(Res.string.limit_hours, Res.string.limit_routes, Res.string.limit_prices, Res.string.limit_live, Res.string.limit_repair).forEach { Text("•  ${stringResource(it)}", color = c.textMuted, fontFamily = f.body) }
        Text(stringResource(Res.string.snapshot_dates), color = c.text, fontFamily = f.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        dates.groupBy { it.city }.forEach { (city, entries) ->
            entries.groupBy { it.date }.forEach { (date, dated) ->
                val categories = dated.map { entry -> when (entry.category) {
                    "attractions" -> stringResource(Res.string.snapshot_attractions)
                    "hotel" -> stringResource(Res.string.snapshot_hotels)
                    "food" -> stringResource(Res.string.snapshot_food)
                    "transport" -> stringResource(Res.string.snapshot_transit)
                    else -> entry.category
                } }.distinct().joinToString(", ")
                Text("$city · $categories · $date", color = c.textMuted, fontFamily = f.body, fontSize = 13.sp)
            }
        }
        Text(stringResource(Res.string.parity), color = c.textMuted, fontFamily = f.body, fontSize = 13.sp)
        Text(stringResource(Res.string.version), color = c.textMuted, fontFamily = f.body, fontSize = 13.sp)
    }
}
