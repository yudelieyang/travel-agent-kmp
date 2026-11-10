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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import travel.theme.*
import travel_agent_kmp.composeapp.generated.resources.*

private data class Preset(val id: String, val title: String, val query: String)
private fun presets(): List<Preset> {
    val source = requireNotNull(TravelEngine::class.java.classLoader.getResource("data/presets.ts")).readText()
    return Regex("\\{ id: '([^']+)', label: '([^']+)'.*?query: '([^']+)'").findAll(source).map { Preset(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }.toList()
}

@Composable fun App(cities: Int, snapshots: Int, mock: Int) {
    TravelTheme {
        val colors = LocalTravelColors.current
        var query by remember { mutableStateOf("") }
        var result by remember { mutableStateOf<EngineRun?>(null) }
        var running by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        var screen by remember { mutableStateOf("home") }
        var repairOrigin by remember { mutableStateOf("run") }
        var stage by remember { mutableIntStateOf(0) }
        var job by remember { mutableStateOf<Job?>(null) }
        val scope = rememberCoroutineScope()
        val plannerMutex = remember { Mutex() }
        PlatformBackHandler(screen != "home") {
            job?.cancel()
            screen = when (screen) { "repair" -> repairOrigin; "result", "clarify" -> "run"; else -> "home" }
        }
        fun run(text: String) {
            if (text.isBlank()) return
            job?.cancel()
            query = text; running = true; failed = false; result = null; stage = 0; screen = "run"
            job = scope.launch {
                val value = runCatching { withContext(Dispatchers.Default) { plannerMutex.withLock { TravelEngine().run(text) } } }
                coroutineContext.ensureActive()
                result = value.getOrNull(); failed = value.isFailure; running = false
                if (value.isFailure) { screen = "home"; return@launch }
                repeat(5) { index -> stage = index; delay(if (index == 3 && result?.result?.repair != null) 1050 else 700) }
                screen = if (result?.result?.status == "needs_clarification") "clarify" else "result"
            }
        }
        Box(Modifier.fillMaxSize().background(colors.background)) {
            ContourBackground(Modifier.matchParentSize())
            BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
                val wide = maxWidth >= 840.dp
                val medium = maxWidth >= 600.dp && !wide
                if (wide && screen in listOf("home", "run", "result") && !(screen == "result" && result?.result?.status == "error")) {
                    WidePage(cities, query, { query = it }, ::run, result, stage, running, failed, onAbout = { screen = "about" }, onRepair = { repairOrigin = screen; screen = "repair" }, onEdit = { result = null; screen = "home" })
                } else Box(if (medium) Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(horizontal = 24.dp) else Modifier.fillMaxSize()) {
                key(screen) {
                if (screen == "home") Home(cities, query, { query = it }, ::run, running, failed, onAbout = { screen = "about" })
                else if (screen == "about") AboutPage(onBack = { screen = "home" })
                else if (result == null) Text(stringResource(Res.string.planning), Modifier.padding(24.dp), color = colors.text)
                else if (screen == "repair") RepairPage(result!!, onBack = { screen = repairOrigin })
                else if (screen == "clarify") ClarificationPage(result!!, onBack = { screen = "run" }, onRun = ::run)
                else if (screen == "result" && result!!.result.status == "error") FailurePage(result!!, onBack = { screen = "run" }, onRun = ::run)
                else if (screen == "result") ResultPage(result!!, onBack = { screen = "run" }, onEdit = { screen = "home" }, onStages = { stage = 4; screen = "run" }, onRepair = { repairOrigin = "result"; screen = "repair" })
                else RunPage(result!!, stage, onBack = { job?.cancel(); screen = "home" }, onSkip = { job?.cancel(); stage = 4; screen = if (result?.result?.status == "needs_clarification") "clarify" else "result" }, onRepair = { job?.cancel(); repairOrigin = "run"; screen = "repair" })
                }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable internal fun Home(cities: Int, query: String, onQuery: (String) -> Unit, run: (String) -> Unit, running: Boolean, failed: Boolean, onAbout: () -> Unit) {
    val c = LocalTravelColors.current; val fonts = LocalTravelFonts.current
    var showCities by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<InputPreview?>(null) }
    var previewError by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        preview = null; previewError = false
        if (query.isNotBlank()) {
            delay(300)
            val parsed = withContext(Dispatchers.Default) { runCatching { parseInputPreview(query) } }
            coroutineContext.ensureActive()
            preview = parsed.getOrNull(); previewError = parsed.isFailure
        }
    }
    val scenarios = remember { presets() }; val featured = scenarios.first { it.id == "repair" }
    if (showCities) ModalBottomSheet(onDismissRequest = { showCities = false }, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Offline cities", color = c.text, fontFamily = fonts.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            CitySuggestions.all.forEach { city ->
                Text(city, Modifier.fillMaxWidth().clickable { onQuery(selectCity(query, city)); showCities = false }.padding(vertical = 12.dp), color = c.text, fontFamily = fonts.body)
            }
        }
    }
    val featuredRequirements = remember(featured.query) { RequirementsParser().parse(featured.query) }
    val featuredDescription = listOfNotNull(featuredRequirements.destination, featuredRequirements.duration_days?.let { stringResource(Res.string.days, it) }, featuredRequirements.travelers?.let { if (it == 1) stringResource(Res.string.one_traveler) else stringResource(Res.string.travelers, it) }, featuredRequirements.budget_amount?.let { "$${MoneyFormatting.format(it)}" }).joinToString(" · ")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(Res.string.offline), Modifier.clip(RoundedCornerShape(50)).background(c.primaryContainer).padding(horizontal = 14.dp, vertical = 10.dp), color = c.primary, fontFamily = fonts.body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            val aboutDescription = stringResource(Res.string.about)
            TextButton(onClick = onAbout, modifier = Modifier.semantics { contentDescription = aboutDescription }) { Text("ⓘ", color = c.text, fontSize = 22.sp) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.home_title), fontFamily = fonts.title, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = c.text)
            Text(stringResource(Res.string.home_subtitle), fontFamily = fonts.body, fontSize = 15.sp, lineHeight = 22.sp, color = c.textMuted)
        }
        Surface(shape = RoundedCornerShape(20.dp), color = c.surface, shadowElevation = 3.dp) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.your_trip), fontFamily = fonts.body, fontWeight = FontWeight.SemiBold, color = c.text)
                BasicTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).border(1.dp, c.inputBorder, RoundedCornerShape(12.dp)).padding(14.dp), textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontFamily = fonts.body, fontSize = 16.sp, letterSpacing = 0.sp, fontFeatureSettings = "tnum"), decorationBox = { inner -> Box { if (query.isEmpty()) Text(stringResource(Res.string.input_hint), color = c.textMuted, fontFamily = fonts.body); inner() } })
                preview?.let { PreviewContent(it, onCity = { city -> onQuery(selectCity(query, city)) }, onAllCities = { showCities = true }) }
                if (previewError) Text("We couldn't preview this request. You can still plan it.", color = c.warning, fontSize = 12.sp)
                if (query.isEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("City *", "Days *", "Travelers", "Budget", "Interests").forEach { label ->
                        Text(label, Modifier.clip(RoundedCornerShape(50)).background(c.segmentTrack).padding(horizontal = 10.dp, vertical = 7.dp), color = c.textMuted, fontFamily = fonts.body, fontSize = 12.sp)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.Center, itemVerticalAlignment = Alignment.CenterVertically) {
                    Text("* needed to plan · works offline for $cities US cities", color = c.textMuted, fontFamily = fonts.body, fontSize = 12.sp)
                    Text("·", color = c.textMuted, fontSize = 12.sp)
                    Text("See cities", Modifier.clickable { showCities = true }.padding(vertical = 8.dp), color = c.primary, fontFamily = fonts.body, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(onClick = { run(query) }, enabled = query.isNotBlank() && !running, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(disabledContainerColor = c.segmentTrack, disabledContentColor = c.textMuted)) { Text(stringResource(Res.string.plan_trip), fontFamily = fonts.body, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); Text("→") }
                if (failed) Text(stringResource(Res.string.error), color = c.error)
            }
        }
        Text(stringResource(Res.string.scenarios), fontFamily = fonts.title, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.hero).clickable { run(featured.query) }.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.featured), color = c.heroWarm, fontFamily = fonts.body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = .5.sp)
            Text(featured.title, color = c.onHero, fontFamily = fonts.title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(featuredDescription, color = c.onHeroMuted, fontFamily = fonts.body, fontSize = 13.sp, lineHeight = 19.sp)
        }
        scenarios.filter { it != featured }.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { preset ->
                    Row(Modifier.weight(1f).heightIn(min = 68.dp).clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(16.dp)).clickable { run(preset.query) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(preset.title, Modifier.weight(1f), fontFamily = fonts.body, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.text); Text("↗", color = c.primary)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text(stringResource(Res.string.footer, cities), color = c.textMuted, fontFamily = fonts.body, fontSize = 13.sp, lineHeight = 19.sp)
        TextButton(onClick = onAbout) { Text(stringResource(Res.string.data_limits)) }
    }
}
