package travel
import kotlinx.serialization.json.*
import kotlin.test.*
class FixtureDatasetValidationTest {
    @Test fun rejectsDuplicateFixtureIds() {
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream("data/travel/us/cities.json"))
        val data = Json.parseToJsonElement(stream.bufferedReader().readText()).jsonObject
        val city = data["cities"]!!.jsonArray.first().jsonObject
        val groups = city["candidates"]!!.jsonObject
        val category = groups.keys.first()
        val rows = groups[category]!!.jsonArray
        val changedGroups = JsonObject(groups + (category to JsonArray(rows + rows.first())))
        val changedCity = JsonObject(city + ("candidates" to changedGroups))
        val changed = JsonObject(data + ("cities" to JsonArray(listOf(changedCity) + data["cities"]!!.jsonArray.drop(1))))
        assertFails { SnapshotValidation.fixtureDataset(changed) }
    }
}
