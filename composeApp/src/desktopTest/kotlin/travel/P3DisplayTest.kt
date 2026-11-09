package travel

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import travel.theme.TravelTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.sp
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

class P3DisplayTest {
    @get:Rule val compose = createComposeRule()
    private fun runWithBudget(amount: Int) = TravelEngine().run("Please build a three-day trip to Austin for one traveler. My total budget is $$amount.")

    @Test fun validationShowsFailureBeforeRepairPasses() {
        val run = runWithBudget(350)
        compose.setContent { TravelTheme(dark = false) { RunPage(run, 4, {}, {}, {}) } }
        compose.onNodeWithTag("stage_3_failed", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("stage_4_passed", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("1 traveler").assertExists()
        compose.onNodeWithText("≤ $350 total · HARD").assertExists()
    }
    @Test fun successfulRepairShowsPerMealAndDailyColumns() {
        val run = runWithBudget(350)
        compose.setContent { TravelTheme(dark = false) { RepairPage(run, {}) } }
        compose.onNodeWithText("Day 1").assertExists()
        compose.onNodeWithText("+$44.00").assertExists()
        compose.onNodeWithText("Bouldin Creek Cafe · $18.00 each").assertExists()
        compose.onNodeWithText("After").assertExists()
    }
    @Test fun failedRepairOnlyShowsInitialCosts() {
        val run = runWithBudget(1)
        compose.setContent { TravelTheme(dark = false) { RepairPage(run, {}) } }
        compose.onNodeWithText("Day 1").assertExists()
        compose.onNodeWithText("Before").assertExists()
        compose.onNodeWithText("After").assertDoesNotExist()
        compose.onNodeWithText("What changed").assertDoesNotExist()
        compose.onNodeWithText("Validation failed").assertExists()
    }
    @Test fun dayTabsAndLegendUseHumanLabels() {
        val run = runWithBudget(350)
        compose.setContent { TravelTheme(dark = false) { ResultPage(run, {}, {}, {}, {}) } }
        compose.onNodeWithText("Day 1 · $135").assertExists()
        compose.onNodeWithText("Day 3 · $47").assertExists()
        compose.onNodeWithText("Hotel $176").assertExists()
        compose.onNodeWithText("Food $126").assertExists()
        compose.onNodeWithText("Sights $15").assertExists()
        compose.onNodeWithText("Transit $0").assertExists()
    }
    @Test fun pluralTravelerCount() {
        val run = TravelEngine().run("Plan a 2-day trip to Boston for 2 travelers under $2000 total.")
        compose.setContent { TravelTheme(dark = false) { RunPage(run, 4, {}, {}, {}) } }
        compose.onNodeWithText("2 travelers").assertExists()
    }
    @Test fun bodyAndButtonTypographyUseTabularDigits() {
        var styles = emptyList<androidx.compose.ui.text.TextStyle>()
        compose.setContent { TravelTheme(dark = false) { styles = listOf(MaterialTheme.typography.bodyLarge, MaterialTheme.typography.bodyMedium, MaterialTheme.typography.labelLarge, MaterialTheme.typography.labelSmall) } }
        compose.runOnIdle { styles.forEach { assertEquals(0.sp, it.letterSpacing); assertEquals("tnum", it.fontFeatureSettings) } }
    }
    @Test fun largeFontStatusAndSavingsDoNotOverlap() {
        val run = runWithBudget(350)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.3f)) {
                TravelTheme(dark = false) { Box(Modifier.size(360.dp, 900.dp)) { ResultPage(run, {}, {}, {}, {}) } }
            }
        }
        val status = compose.onNodeWithText("Passed after 1 repair").getUnclippedBoundsInRoot()
        val savings = compose.onNodeWithText("saved $77.00").getUnclippedBoundsInRoot()
        assertFalse(status.left < savings.right && status.right > savings.left && status.top < savings.bottom && status.bottom > savings.top, "Status and savings overlap at 1.3× font scale")
    }
}
