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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import travel.theme.*
import travel_agent_kmp.composeapp.generated.resources.*

@Composable fun ClarificationPage(run: EngineRun, onBack: () -> Unit, onRun: (String) -> Unit) {
    val c = LocalTravelColors.current; val f = LocalTravelFonts.current; val r = run.result
    var query by remember { mutableStateOf(r.query) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row { BackButton(onBack); Text(r.requirements.destination ?: stringResource(Res.string.home_title), Modifier.padding(12.dp), color = c.text, fontFamily = f.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
        Text("“${r.query}”", Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(16.dp), color = c.textMuted, fontFamily = f.body)
        listOf("destination" to stringResource(Res.string.destination), "duration" to stringResource(Res.string.duration)).forEach { (key, label) ->
            val missing = key in r.missing_fields
            val value = if (key == "destination") r.requirements.destination else r.requirements.duration_days?.let { stringResource(Res.string.days, it) }
            Text("${if (missing) "!" else "✓"}  $label · ${if (missing) stringResource(Res.string.missing) else value ?: "—"}", color = if (missing) c.warning else c.success, fontFamily = f.body, fontWeight = FontWeight.Medium)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.warningContainer).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(Res.string.clarify_label), color = c.onWarningContainer, fontFamily = f.body, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .5.sp)
            Text(r.clarification_question.orEmpty(), color = c.onWarningContainer, fontFamily = f.title, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
            BasicTextField(query, { query = it }, Modifier.fillMaxWidth().heightIn(min = 100.dp).clip(RoundedCornerShape(12.dp)).background(c.surface).border(1.dp, c.warningBorder, RoundedCornerShape(12.dp)).padding(14.dp), textStyle = TextStyle(color = c.text, fontFamily = f.body, fontSize = 16.sp, letterSpacing = 0.sp, fontFeatureSettings = "tnum"))
            Button(onClick = { onRun(query) }, enabled = query.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text(stringResource(Res.string.run_again)) }
        }
        Text(stringResource(Res.string.clarify_footer), color = c.textMuted, fontFamily = f.body, fontSize = 13.sp, lineHeight = 19.sp)
    }
}
