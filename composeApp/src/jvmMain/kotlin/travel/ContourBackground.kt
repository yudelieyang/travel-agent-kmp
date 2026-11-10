package travel

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import travel.theme.LocalTravelColors

@Composable internal fun ContourBackground(modifier: Modifier = Modifier, motion: Boolean = rememberContourMotionEnabled()) {
    val colors = LocalTravelColors.current
    val dark = isSystemInDarkTheme()
    val anchor = remember { floatArrayOf(0f) }
    val phase: State<Float> = if (motion) {
        val transition = rememberInfiniteTransition(label = "contour drift")
        transition.animateFloat(anchor[0], anchor[0] + 1f, infiniteRepeatable(tween(180_000, easing = LinearEasing), RepeatMode.Restart), label = "contour phase")
    } else remember { mutableFloatStateOf(0f) }
    DisposableEffect(motion) { onDispose { if (motion) anchor[0] = phase.value % 1f } }
    val normal = colors.primary.copy(alpha = if (dark) .12f else .11f)
    val index = colors.primary.copy(alpha = if (dark) .22f else .20f)
    Canvas(modifier.layout { measurable, constraints ->
        val width = constraints.maxWidth; val height = constraints.maxHeight
        val extra = 260.dp.roundToPx()
        val placeable = measurable.measure(Constraints.fixed(width + extra, height + extra))
        layout(width, height) { placeable.place(0, 0) }
    }.graphicsLayer {
        val offset = -(if (motion) phase.value % 1f else 0f) * 260.dp.toPx()
        translationX = offset; translationY = offset
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithCache {
        val scale = 1.dp.toPx(); val tile = ContourField.period.toFloat() * scale
        val lines = ContourField.contours()
        fun path(indices: List<Int>) = Path().apply {
            indices.forEach { i -> lines[i].forEach { line ->
                moveTo((line.a.x * scale).toFloat(), (line.a.y * scale).toFloat())
                lineTo((line.b.x * scale).toFloat(), (line.b.y * scale).toFloat())
            } }
        }
        val regular = path(listOf(0, 1, 2, 4, 5, 6)); val primary = path(listOf(3))
        val regularStroke = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val indexStroke = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        onDrawBehind {
            val cols = ceil(size.width / tile).toInt(); val rows = ceil(size.height / tile).toInt()
            for (y in 0..rows) for (x in 0..cols) translate(x * tile, y * tile) {
                drawPath(regular, normal, style = regularStroke); drawPath(primary, index, style = indexStroke)
            }
        }
    }) {}
}
