package travel

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import travel.theme.TravelTheme

class PlanUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun repairedBudget() {
        val run = TravelEngine().run("Please build a three-day trip to Austin for one traveler. My total budget is $350.")
        compose.setContent { TravelTheme(dark = false) { ResultPage(run, {}, {}, {}, {}) } }
        compose.onNodeWithText("$317.00").assertIsDisplayed()
        compose.onNodeWithText("of $350.00 · $33.00 left").assertIsDisplayed()
    }
    @Test fun failedRepair() {
        val run = TravelEngine().run("Please build a three-day trip to Austin for one traveler. My total budget is $1.")
        compose.setContent { TravelTheme(dark = false) { ResultPage(run, {}, {}, {}, {}) } }
        compose.onNodeWithText("Couldn't fit the budget").assertIsDisplayed()
        compose.onNodeWithText("HARD_BUDGET_EXCEEDED · $394.00 / $1.00").assertIsDisplayed()
    }
    @Test fun clarification() {
        val run = TravelEngine().run("Plan a trip to Boston.")
        compose.setContent { TravelTheme(dark = false) { ClarificationPage(run, {}, {}) } }
        compose.onNodeWithText("For how many days, or on which start and end dates?").assertIsDisplayed()
    }
}
