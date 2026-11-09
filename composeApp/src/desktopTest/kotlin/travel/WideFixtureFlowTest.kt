package travel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import kotlinx.serialization.json.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WideFixtureFlowTest(private val name: String) {
    @get:Rule val compose = createComposeRule()
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun cases() = listOf("preset-snapshot", "preset-repair", "preset-deterministic", "preset-ambiguity", "preset-correction", "preset-tradeoff", "clarification", "repair-failure").map { arrayOf(it) }
    }
    @Test fun completeWideFixtureFlow() {
        val fixture = Json.parseToJsonElement(requireNotNull(javaClass.classLoader.getResourceAsStream("golden/e2e/$name.json")).bufferedReader().use { it.readText() }).jsonObject
        val status = fixture.getValue("status").jsonPrimitive.content
        val expected = when (status) {
            "needs_clarification" -> fixture.getValue("clarification_question").jsonPrimitive.content
            "error" -> "Couldn't fit the budget"
            else -> "$${MoneyFormatting.format(BigDecimal(fixture.getValue("budget").jsonObject.getValue("estimated_total_cost").jsonPrimitive.content))}"
        }
        compose.setContent { Box(Modifier.size(1200.dp, 900.dp)) { App(12, 114, 150) } }
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement(fixture.getValue("query").jsonPrimitive.content)
        compose.onAllNodesWithText("Plan trip")[0].performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(expected).assertIsDisplayed()
    }
}
