package travel

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import travel.theme.TravelTheme

class FailurePageTest {
    @get:Rule val compose = createComposeRule()
    private val engine = TravelEngine()
    @Test fun cityMatching() {
        assertEquals("Boston", CitySuggestions.closest("Bostn").first())
        assertEquals("Chicago", CitySuggestions.closest("Chicgo").first())
        assertEquals("Miami", CitySuggestions.closest("Mimai").first())
        assertTrue(CitySuggestions.closest("wonderland").isEmpty())
    }
    @Test fun unknownCityCanReplaceAndRun() {
        compose.setContent { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(400.dp, 800.dp)) { App(12, 114, 150) } }
        compose.onNode(hasSetTextAction()).performTextReplacement("Plan a 2-day trip to wonderland for 1 traveler under $500 total.")
        compose.onNodeWithText("Plan trip").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Skip").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("We couldn't find “wonderland”").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("This offline build covers 12 US cities", substring = true).assertExists()
        compose.onNodeWithText("Boston").performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Skip").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Boston").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Boston").assertExists()
    }
    @Test fun noCandidates() {
        val run = engine.run("Plan a 3-day trip to Boston for 1 traveler. Hotel spending must stay under $1 total.")
        compose.setContent { TravelTheme(dark = false) { FailurePage(run, {}, {}) } }
        compose.onNodeWithText("No matching options found").assertExists()
        compose.onNodeWithText("The offline dataset has no options matching the request.").assertExists()
        compose.onNodeWithText("Engine details: error · search_hotels: NO_RESULTS").assertExists()
        compose.onNodeWithText("Run again").assertExists()
    }
    @Test fun failedBudget() {
        val run = engine.run("Please build a three-day trip to Austin for one traveler. My total budget is $1.")
        compose.setContent { TravelTheme(dark = false) { FailurePage(run, {}, {}) } }
        compose.onNodeWithText("Couldn't fit the budget").assertExists()
        compose.onNodeWithText("The least-cost repair still exceeds the budget.").assertExists()
        compose.onNodeWithText("Engine details: error · Hard budget exceeded").assertExists()
        compose.onNodeWithText("Run again").assertExists()
    }
    @Test fun hotelBudgetFailure() {
        val base = engine.run("Plan a 2-day trip to Boston for 1 traveler under $500 total.")
        val run = base.copy(result = base.result.copy(status = "error", errors = listOf("Hotel budget exceeded")))
        compose.setContent { TravelTheme(dark = false) { FailurePage(run, {}, {}) } }
        compose.onNodeWithText("Couldn't fit the budget").assertExists()
        compose.onNodeWithText("The least-cost repair still exceeds the budget.").assertExists()
        compose.onNodeWithText("Engine details: error · Hotel budget exceeded").assertExists()
        compose.onNodeWithText("Run again").assertExists()
    }
    @Test fun internalFailure() {
        val base = engine.run("Plan a 2-day trip to Boston for 1 traveler under $500 total.")
        val run = base.copy(result = base.result.copy(status = "error", errors = listOf("Daily cost does not match activities")))
        compose.setContent { TravelTheme(dark = false) { FailurePage(run, {}, {}) } }
        compose.onNodeWithText("Something in this request couldn't be planned offline").assertExists()
        compose.onNodeWithText("An internal planning check did not complete.").assertExists()
        compose.onNodeWithText("Run again").assertExists()
    }
}

@androidx.compose.runtime.Composable private fun TextResult(status: String) { androidx.compose.material3.Text(status) }
