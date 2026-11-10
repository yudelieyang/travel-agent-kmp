package travel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class InputGuidanceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun requiredFieldsFollowPreflight() {
        assertEquals(listOf("destination", "duration"), TravelEngine().run("plan a trip").result.missing_fields)
        assertEquals("Boston", selectCity("", "Boston"))
        assertEquals("3 days in Chicago for 2 people.", selectCity("3 days in Boston for 2 people.", "Chicago"))
        assertEquals("Plan a 2-day trip to Boston for 1 traveler.", selectCity("Plan a 2-day trip to wonderland for 1 traveler.", "Boston"))
        assertEquals("3 days in New York City.", selectCity("3 days in NYC.", "New York City"))
        assertEquals("3 days in Boston.", selectCity("3 days.", "Boston"))
        assertEquals("3 days in Boston", selectCity("3 days in", "Boston"))
    }

    @Test fun previewWarnsAndCanReplaceUnknownCity() {
        compose.setContent { Box(Modifier.size(400.dp, 800.dp)) { App(12, 114, 150) } }
        compose.onNode(hasSetTextAction()).performTextReplacement("I wanna have a 4 Days trip to wonderland. And i prefer Fried chicken.")
        compose.waitUntil(20_000) { compose.onAllNodesWithText("City: wonderland — not found").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("✓ Days · 4 days").assertExists()
        compose.onNodeWithText("Boston").performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("✓ City · Boston").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("City: wonderland — not found").assertDoesNotExist()
    }

    @Test fun coverageWarningQuotesEvidence() {
        compose.setContent { Box(Modifier.size(400.dp, 800.dp)) { App(12, 114, 150) } }
        compose.onNode(hasSetTextAction()).performTextReplacement("Boston is fine, but do not include zoos.")
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Some details may be ignored offline", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Some details may be ignored offline", substring = true) and hasText("zoos", substring = true)).assertExists()
        compose.onNodeWithText("Plan trip").assertIsEnabled()
    }

    @Test fun showsGuidanceAndInsertsCity() {
        compose.setContent { Box(Modifier.size(400.dp, 800.dp)) { App(12, 114, 150) } }
        compose.onNodeWithText("e.g. 3 days in Boston for 2 people, under $800 total. We like museums.").assertExists()
        listOf("City *", "Days *", "Travelers", "Budget", "Interests", "See cities").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("See cities").performClick()
        compose.onNodeWithText("Offline cities").assertExists()
        compose.onNodeWithText("Boston").performClick()
        compose.onNode(hasSetTextAction()).assertTextEquals("Boston")
        compose.onNodeWithText("See cities").performClick()
        compose.onNodeWithText("Chicago").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assertTextEquals("Chicago")
    }
}
