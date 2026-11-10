package travel

import kotlin.math.cos
import kotlin.math.PI
import kotlinx.serialization.json.*

internal data class RouteCity(val name: String, val latitude: Double, val longitude: Double)
internal data class RoutePoint(val x: Double, val y: Double)
internal data class RouteEdge(val from: Int, val to: Int)

internal object CityRoutes {
    val cities: List<RouteCity> by lazy {
        val loader = SnapshotLoader()
        CitySuggestions.all.mapNotNull { city ->
            val points = listOf("attractions", "hotel", "food").flatMap { category ->
                loader.candidates(city, category).orEmpty().mapNotNull { item ->
                    val row = item.jsonObject
                    val lat = row["latitude"]?.jsonPrimitive?.doubleOrNull
                    val lon = row["longitude"]?.jsonPrimitive?.doubleOrNull
                    if (lat == null || lon == null || row["provider"]?.jsonPrimitive?.contentOrNull != "openstreetmap") null else lat to lon
                }
            }
            if (points.isEmpty()) null else RouteCity(city, points.map { it.first }.average(), points.map { it.second }.average())
        }
    }
    fun planar(cities: List<RouteCity>): List<RoutePoint> {
        if (cities.isEmpty()) return emptyList()
        val factor = cos(cities.map { it.latitude }.average() * PI / 180.0)
        return cities.map { RoutePoint(it.longitude * factor, -it.latitude) }
    }
    fun tree(points: List<RoutePoint>): List<RouteEdge> {
        if (points.size < 2) return emptyList()
        val selected = BooleanArray(points.size); selected[0] = true
        return buildList {
            repeat(points.size - 1) {
                var bestFrom = -1; var bestTo = -1; var best = Double.POSITIVE_INFINITY
                for (i in points.indices) if (selected[i]) for (j in points.indices) if (!selected[j]) {
                    val dx = points[i].x - points[j].x; val dy = points[i].y - points[j].y
                    val distance = dx * dx + dy * dy
                    if (distance < best || (distance == best && (bestTo < 0 || j < bestTo || j == bestTo && i < bestFrom))) {
                        best = distance; bestFrom = i; bestTo = j
                    }
                }
                selected[bestTo] = true; add(RouteEdge(bestFrom, bestTo))
            }
        }
    }
    fun project(points: List<RoutePoint>, width: Double, height: Double, padding: Double): List<RoutePoint> {
        if (points.isEmpty()) return emptyList()
        val minX = points.minOf { it.x }; val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }; val maxY = points.maxOf { it.y }
        val availableWidth = (width - 2 * padding).coerceAtLeast(0.0)
        val availableHeight = (height - 2 * padding).coerceAtLeast(0.0)
        val dx = maxX - minX; val dy = maxY - minY
        val scale = minOf(if (dx == 0.0) Double.POSITIVE_INFINITY else availableWidth / dx, if (dy == 0.0) Double.POSITIVE_INFINITY else availableHeight / dy).let { if (it.isFinite()) it else 0.0 }
        val left = (width - dx * scale) / 2; val top = (height - dy * scale) / 2
        return points.map { RoutePoint(left + (it.x - minX) * scale, top + (it.y - minY) * scale) }
    }
}
