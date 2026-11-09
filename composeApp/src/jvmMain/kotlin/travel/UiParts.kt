package travel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import travel.theme.LocalTravelColors
import travel_agent_kmp.composeapp.generated.resources.Res
import travel_agent_kmp.composeapp.generated.resources.cancel
import java.math.BigDecimal
import java.math.RoundingMode

@Composable fun BackButton(onBack: () -> Unit) {
    val description = stringResource(Res.string.cancel)
    val color = LocalTravelColors.current.primary
    Box(Modifier.size(44.dp).semantics { contentDescription = description }.clickable(onClick = onBack), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(24.dp)) {
            val stroke = 2.dp.toPx()
            drawLine(color, Offset(size.width * .78f, size.height * .5f), Offset(size.width * .22f, size.height * .5f), stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width * .22f, size.height * .5f), Offset(size.width * .48f, size.height * .24f), stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width * .22f, size.height * .5f), Offset(size.width * .48f, size.height * .76f), stroke, cap = StrokeCap.Round)
        }
    }
}

fun compactMoney(value: BigDecimal): String = value.setScale(0, RoundingMode.DOWN).toPlainString()
fun legendMoney(value: BigDecimal): String = MoneyFormatting.format(value).removeSuffix(".00")
