package travel.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AssistedGuidanceUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Before fun launch() {
        val p = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("am start --activity-clear-task --activity-new-task -n travel.android/.MainActivity")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(p).bufferedReader().use { it.readText() }
        compose.waitUntil(20_000) { runCatching { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
    }
    @Test fun insertAndReplaceCity() {
        compose.onNodeWithText("See cities").performClick()
        compose.onNodeWithText("Boston").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Boston")
        compose.onNodeWithText("See cities").performScrollTo().performClick()
        compose.onNodeWithText("Chicago").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Chicago")
    }
    @Test fun previewAndUnknownCity() {
        compose.onNode(hasSetTextAction()).performTextReplacement("I wanna have a 4 Days trip to wonderland. And i prefer Fried chicken.")
        compose.waitUntil(20_000) { compose.onAllNodes(hasText("Understood so far")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("City: wonderland — not found").assertExists()
        compose.onNodeWithText("✓ Days · 4 days").assertExists()
        compose.onNodeWithText("isn't one of the 12 cities", substring = true).assertExists()
        compose.onNodeWithText("Plan trip").assertIsEnabled()
    }
}
