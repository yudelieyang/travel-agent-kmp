package travel.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.Before

abstract class PlanUiCases {
    abstract val compose: ComposeTestRule
    private fun query(name: String): String {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        return Json.parseToJsonElement(assets.open("golden/e2e/$name.json").bufferedReader().use { it.readText() }).jsonObject["query"]!!.jsonPrimitive.content
    }
    private fun run(name: String, expected: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(query(name))
        compose.onNodeWithText("Plan trip").performClick()
        compose.waitUntil(20_000) { compose.onAllNodes(androidx.compose.ui.test.hasText(expected)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(expected).assertIsDisplayed()
    }
    @Test fun repairedBudget() { run("preset-repair", "$317.00"); compose.onNodeWithText("of $350.00 · $33.00 left").assertIsDisplayed() }
    @Test fun snapshot() { run("preset-snapshot", "$223.00") }
    @Test fun preferences() { run("preset-deterministic", "$343.00") }
    @Test fun ambiguity() { run("preset-ambiguity", "$433.00") }
    @Test fun correction() { run("preset-correction", "$433.00") }
    @Test fun tradeoff() { run("preset-tradeoff", "$433.00") }
    @Test fun failedRepair() { run("repair-failure", "Couldn't fit the budget"); compose.onNodeWithText("HARD_BUDGET_EXCEEDED · $394.00 / $1.00").assertIsDisplayed() }
    @Test fun clarification() { run("clarification", "For how many days, or on which start and end dates?") }
}

class PlanUiTest : PlanUiCases() {
    @get:Rule override val compose = createAndroidComposeRule<MainActivity>()
}

class AssistedPlanUiTest : PlanUiCases() {
    @get:Rule override val compose = createEmptyComposeRule()
    @Before fun launch() {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "am start --activity-clear-task --activity-new-task -n travel.android/.MainActivity"
        )
        android.os.ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readText() }
        compose.waitUntil(20_000) { runCatching { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
    }
}
