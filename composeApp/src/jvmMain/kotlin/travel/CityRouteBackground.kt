package travel

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import travel.theme.LocalTravelColors

@Composable internal fun CityRouteBackground(modifier: Modifier = Modifier, motion: Boolean = rememberRouteMotionEnabled()) {
    val points = remember { CityRoutes.planar(CityRoutes.cities) }
    val edges = remember(points) { CityRoutes.tree(points) }
    val colors = LocalTravelColors.current
    val dark = isSystemInDarkTheme()
    val anchor = remember { floatArrayOf(0f) }
    val seconds: State<Float> = if (motion) {
        val transition = rememberInfiniteTransition(label = "city routes")
        transition.animateFloat(anchor[0], anchor[0] + 27720f, infiniteRepeatable(tween(27720000, easing = LinearEasing), RepeatMode.Restart), label = "route clock")
    } else remember { mutableFloatStateOf(0f) }
    DisposableEffect(motion) { onDispose { if (motion) anchor[0] = seconds.value % 27720f } }
    val arcColor = colors.primary.copy(alpha = if (dark) .30f else .28f)
    val cityColor = colors.primary.copy(alpha = .55f)
    val dotColor = if (dark) colors.heroWarm else colors.warning
    Canvas(modifier.graphicsLayer().drawWithCache {
        val projected = CityRoutes.project(points, size.width.toDouble(), size.height.toDouble(), 16.dp.toPx().toDouble()).map { Offset(it.x.toFloat(), it.y.toFloat()) }
        val paths = edges.map { edge ->
            val a = projected[edge.from]; val b = projected[edge.to]
            val dx = b.x - a.x; val dy = b.y - a.y
            val control = Offset((a.x + b.x) / 2 - dy * .22f, (a.y + b.y) / 2 + dx * .22f)
            Path().apply { moveTo(a.x, a.y); quadraticTo(control.x, control.y, b.x, b.y) }
        }
        val measures = paths.map { path -> PathMeasure().apply { setPath(path, false) } }
        val dash = floatArrayOf(3.dp.toPx(), 7.dp.toPx())
        val strokes = List(360) { i -> Stroke(width = 1.4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(dash, i * dash.sum() / 360f)) }
        onDrawBehind {
            val t = seconds.value
            val phaseIndex = if (motion) ((t % 6f) / 6f * strokes.size).toInt().coerceIn(0, strokes.lastIndex) else 0
            val stroke = strokes[phaseIndex]
            paths.forEachIndexed { i, path ->
                drawPath(path, arcColor, style = stroke)
                if (motion) {
                    val travel = ((t + i * 1.3f) % (7 + i % 5)) / (7 + i % 5)
                    val pos = measures[i].getPosition(measures[i].length * travel)
                    if (pos.isSpecified) drawCircle(dotColor.copy(alpha = .8f), 3.2.dp.toPx(), pos)
                }
            }
            projected.forEachIndexed { i, point ->
                drawCircle(cityColor, 3.5.dp.toPx(), point)
                if (motion) {
                    val p = ((t + i * .7f) % 3.6f) / 3.6f
                    drawCircle(colors.primary.copy(alpha = .55f * (1 - p)), 3.5.dp.toPx() * (.4f + 2f * p), point, style = Stroke(1.2.dp.toPx()))
                }
            }
        }
    }) {}
}
