package travel

import org.junit.Test
import kotlin.test.*

class CityRoutesTest {
    @Test fun cityTreeIsStableAndSpansAllCities() {
        val cities = CityRoutes.cities
        assertEquals(12, cities.size)
        val edges = CityRoutes.tree(CityRoutes.planar(cities))
        assertEquals(cities.size - 1, edges.size)
        assertEquals(listOf("Boston–New York City", "New York City–Washington DC", "Washington DC–Columbus", "Columbus–Chicago", "Washington DC–Miami", "Chicago–Denver", "Denver–Las Vegas", "Las Vegas–Los Angeles", "Los Angeles–San Francisco", "San Francisco–Seattle", "Denver–Austin"), edges.map { "${cities[it.from].name}–${cities[it.to].name}" })
        assertEquals(edges, CityRoutes.tree(CityRoutes.planar(cities)))
    }
    @Test fun tiesUseTableOrder() {
        val points = listOf(RoutePoint(0.0, 0.0), RoutePoint(1.0, 0.0), RoutePoint(0.0, 1.0), RoutePoint(1.0, 1.0))
        assertEquals(listOf(RouteEdge(0, 1), RouteEdge(0, 2), RouteEdge(1, 3)), CityRoutes.tree(points))
    }
    @Test fun projectionFitsWithoutDistortion() {
        val input = CityRoutes.planar(CityRoutes.cities)
        for ((width, height) in listOf(360.0 to 180.0, 900.0 to 500.0, 180.0 to 360.0)) {
            val points = CityRoutes.project(input, width, height, 16.0)
            assertTrue(points.all { it.x in 16.0..(width - 16.0) && it.y in 16.0..(height - 16.0) })
            val x = (points[1].x - points[0].x) / (input[1].x - input[0].x)
            val y = (points[1].y - points[0].y) / (input[1].y - input[0].y)
            assertEquals(x, y, 1e-9)
        }
    }
}
