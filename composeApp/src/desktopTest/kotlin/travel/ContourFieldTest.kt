package travel

import org.junit.Test
import kotlin.test.*

class ContourFieldTest {
    @Test fun fieldIsPeriodic() {
        val p = ContourField.period
        for (x in listOf(-520.0, -37.2, 0.0, 29.5, 139.1, 260.0)) for (y in listOf(-80.4, 0.0, 123.7, 520.0)) {
            assertEquals(ContourField.value(x, y), ContourField.value(x + p, y), 1e-12)
            assertEquals(ContourField.value(x, y), ContourField.value(x, y + p), 1e-12)
        }
    }
    @Test fun contoursStayInsideTileAndAreDeterministic() {
        val first = ContourField.contours(); val second = ContourField.contours()
        assertEquals(7, first.size); assertEquals(first, second)
        assertTrue(first.all { it.isNotEmpty() })
        assertTrue(first.flatten().all { segment -> listOf(segment.a, segment.b).all { it.x in 0.0..ContourField.period && it.y in 0.0..ContourField.period } })
    }
    @Test fun oppositeTileEdgesMeet() {
        val p = ContourField.period
        for (level in ContourField.contours()) {
            val endpoints = level.flatMap { listOf(it.a, it.b) }
            fun at(edge: (FieldPoint) -> Boolean, coordinate: (FieldPoint) -> Double) = endpoints.filter(edge).map(coordinate).sorted()
            val left = at({ kotlin.math.abs(it.x) < 1e-8 }, { it.y }); val right = at({ kotlin.math.abs(it.x - p) < 1e-8 }, { it.y })
            val top = at({ kotlin.math.abs(it.y) < 1e-8 }, { it.x }); val bottom = at({ kotlin.math.abs(it.y - p) < 1e-8 }, { it.x })
            assertEquals(left.size, right.size); assertEquals(top.size, bottom.size)
            left.zip(right).forEach { (a, b) -> assertEquals(a, b, 1e-8) }
            top.zip(bottom).forEach { (a, b) -> assertEquals(a, b, 1e-8) }
        }
    }
}
