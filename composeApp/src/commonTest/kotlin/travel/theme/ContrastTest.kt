package travel.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

class ContrastTest {
    @Test fun textCombinationsMeetWcag() {
        for ((name, c) in listOf("light" to LightColors, "dark" to DarkColors)) {
            val pairs = listOf(
                "text/background" to (c.text to c.background), "muted/background" to (c.textMuted to c.background),
                "text/surface" to (c.text to c.surface), "muted/surface" to (c.textMuted to c.surface),
                "primary/background" to (c.primary to c.background), "primary/surface" to (c.primary to c.surface),
                "error/background" to (c.error to c.background), "error/surface" to (c.error to c.surface),
                "success/background" to (c.success to c.background), "success/surface" to (c.success to c.surface),
                "warning/background" to (c.warning to c.background), "warning/surface" to (c.warning to c.surface),
                "text/mapGround" to (c.text to c.mapGround),
                "disabled/segmentTrack" to (c.textMuted to c.segmentTrack),
                "onPrimary/primary" to (c.onPrimary to c.primary), "primary/primaryContainer" to (c.primary to c.primaryContainer),
                "onHero/hero" to (c.onHero to c.hero), "onHeroMuted/hero" to (c.onHeroMuted to c.hero),
                "heroAccent/hero" to (c.heroAccent to c.hero), "heroWarm/hero" to (c.heroWarm to c.hero),
                "onErrorContainer/errorContainer" to (c.onErrorContainer to c.errorContainer),
                "error/errorContainer" to (c.error to c.errorContainer),
                "onSuccessContainer/successContainer" to (c.onSuccessContainer to c.successContainer),
                "success/successContainer" to (c.success to c.successContainer),
                "onWarningContainer/warningContainer" to (c.onWarningContainer to c.warningContainer),
                "warning/warningContainer" to (c.warning to c.warningContainer),
            )
            for ((label, pair) in pairs) {
                val (foreground, background) = pair
                val contrast = contrast(foreground, background)
                assertTrue(contrast >= 4.5, "$name $label contrast was $contrast")
            }
        }
    }
    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance(); val y = b.luminance()
        return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
    }
}
