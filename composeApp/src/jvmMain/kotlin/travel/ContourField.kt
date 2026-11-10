package travel

import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

internal data class FieldPoint(val x: Double, val y: Double)
internal data class ContourSegment(val a: FieldPoint, val b: FieldPoint)

/** Fixed-seed, periodic field and marching-squares contours in density-independent units. */
internal object ContourField {
    const val period = 260.0
    private val harmonics = listOf(Triple(1, 0, .8), Triple(0, 1, .65), Triple(1, 1, .4), Triple(2, -1, .24), Triple(1, 2, .16), Triple(3, 1, .10))
    private val phases = Random(79421L).let { random -> harmonics.map { random.nextDouble() * 2 * PI } }
    val levels = listOf(-.75, -.50, -.25, 0.0, .25, .50, .75)
    fun value(x: Double, y: Double): Double = harmonics.indices.sumOf { i ->
        val (m, n, amplitude) = harmonics[i]
        amplitude * sin(2 * PI * (m * x + n * y) / period + phases[i])
    }
    fun contours(grid: Int = 44): List<List<ContourSegment>> {
        require(grid > 0)
        val step = period / grid
        val samples = Array(grid + 1) { x -> DoubleArray(grid + 1) { y -> value(x * step, y * step) } }
        return levels.map { level -> buildList {
            for (x in 0 until grid) for (y in 0 until grid) {
                val corners = listOf(FieldPoint(x * step, y * step), FieldPoint((x + 1) * step, y * step), FieldPoint((x + 1) * step, (y + 1) * step), FieldPoint(x * step, (y + 1) * step))
                val values = listOf(samples[x][y], samples[x + 1][y], samples[x + 1][y + 1], samples[x][y + 1])
                val crossings = mutableListOf<FieldPoint>()
                for (i in 0..3) {
                    val next = (i + 1) % 4; val a = values[i]; val b = values[next]
                    if ((a < level && b >= level) || (b < level && a >= level)) {
                        val t = (level - a) / (b - a)
                        crossings.add(FieldPoint(corners[i].x + (corners[next].x - corners[i].x) * t, corners[i].y + (corners[next].y - corners[i].y) * t))
                    }
                }
                if (crossings.size == 2) add(ContourSegment(crossings[0], crossings[1]))
                else if (crossings.size == 4) {
                    val center = value((x + .5) * step, (y + .5) * step)
                    if ((center >= level) == (values[0] >= level)) {
                        add(ContourSegment(crossings[0], crossings[1])); add(ContourSegment(crossings[2], crossings[3]))
                    } else {
                        add(ContourSegment(crossings[0], crossings[3])); add(ContourSegment(crossings[1], crossings[2]))
                    }
                }
            }
        } }
    }
}
