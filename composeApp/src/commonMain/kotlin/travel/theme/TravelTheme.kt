package travel.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import travel_agent_kmp.composeapp.generated.resources.Res
import travel_agent_kmp.composeapp.generated.resources.inter
import travel_agent_kmp.composeapp.generated.resources.jetbrains_mono
import travel_agent_kmp.composeapp.generated.resources.space_grotesk

data class TravelColors(
    val background: Color, val surface: Color, val border: Color, val divider: Color,
    val inputBorder: Color, val segmentTrack: Color, val text: Color, val textMuted: Color,
    val primary: Color, val onPrimary: Color, val primaryContainer: Color,
    val hero: Color, val onHero: Color, val onHeroMuted: Color, val heroAccent: Color, val heroWarm: Color,
    val error: Color, val errorContainer: Color, val onErrorContainer: Color,
    val success: Color, val successContainer: Color, val onSuccessContainer: Color,
    val warning: Color, val warningContainer: Color, val onWarningContainer: Color, val warningBorder: Color,
    val mapGround: Color,
)

val LightColors = TravelColors(
    Color(0xFFF4F6F8), Color.White, Color(0xFFDDE3E8), Color(0xFFEEF1F4), Color(0xFFCBD2D9), Color(0xFFE4E8EC),
    Color(0xFF13202B), Color(0xFF455360), Color(0xFF0A5A5A), Color.White, Color(0xFFDDEFEE),
    Color(0xFF13202B), Color.White, Color(0xFFC5CED6), Color(0xFF8FD3C9), Color(0xFFF2B66D),
    Color(0xFF961911), Color(0xFFFDECEA), Color(0xFF7A1A12), Color(0xFF125B34), Color(0xFFE3F2EA), Color(0xFF15573A),
    Color(0xFF8C3900), Color(0xFFFFF4E5), Color(0xFF8A4B0E), Color(0xFFE2C9A6), Color(0xFFEEF3F2),
)
val DarkColors = TravelColors(
    Color(0xFF0E161D), Color(0xFF16212B), Color(0xFF263440), Color(0xFF263440), Color(0xFF637582), Color(0xFF263440),
    Color(0xFFE6EBEF), Color(0xFFB1BDC7), Color(0xFF5CC2B5), Color(0xFF08211F), Color(0xFF183C3A),
    Color(0xFF1C2A35), Color.White, Color(0xFFC5CED6), Color(0xFF8FD3C9), Color(0xFFF2B66D),
    Color(0xFFFFAFA8), Color(0xFF4D211F), Color(0xFFFFDAD6), Color(0xFF9CE1B8), Color(0xFF183B2A), Color(0xFFBDEBD0),
    Color(0xFFFFD294), Color(0xFF493016), Color(0xFFFFDDB0), Color(0xFF8B6942), Color(0xFF203331),
)

val LocalTravelColors = staticCompositionLocalOf { LightColors }
data class TravelFonts(val title: FontFamily, val body: FontFamily, val code: FontFamily)
val LocalTravelFonts = staticCompositionLocalOf { TravelFonts(FontFamily.Default, FontFamily.Default, FontFamily.Monospace) }

@Composable fun TravelTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val fonts = TravelFonts(
        FontFamily(Font(Res.font.space_grotesk, FontWeight.Normal), Font(Res.font.space_grotesk, FontWeight.SemiBold), Font(Res.font.space_grotesk, FontWeight.Bold)),
        FontFamily(Font(Res.font.inter, FontWeight.Normal), Font(Res.font.inter, FontWeight.Medium), Font(Res.font.inter, FontWeight.SemiBold)),
        FontFamily(Font(Res.font.jetbrains_mono, FontWeight.Medium)),
    )
    val scheme = if (dark) darkColorScheme(primary = c.primary, onPrimary = c.onPrimary, background = c.background, surface = c.surface, onSurface = c.text) else
        lightColorScheme(primary = c.primary, onPrimary = c.onPrimary, background = c.background, surface = c.surface, onSurface = c.text)
    val base = Typography()
    fun TextStyle.body() = copy(fontFamily = fonts.body, letterSpacing = 0.sp, fontFeatureSettings = "tnum")
    val typography = base.copy(
        displayLarge = base.displayLarge.body(), displayMedium = base.displayMedium.body(), displaySmall = base.displaySmall.body(),
        headlineLarge = base.headlineLarge.body(), headlineMedium = base.headlineMedium.body(), headlineSmall = base.headlineSmall.body(),
        titleLarge = base.titleLarge.body(), titleMedium = base.titleMedium.body(), titleSmall = base.titleSmall.body(),
        bodyLarge = base.bodyLarge.body(), bodyMedium = base.bodyMedium.body(), bodySmall = base.bodySmall.body(),
        labelLarge = base.labelLarge.body(), labelMedium = base.labelMedium.body(), labelSmall = base.labelSmall.body(),
    )
    CompositionLocalProvider(LocalTravelColors provides c, LocalTravelFonts provides fonts) {
        MaterialTheme(colorScheme = scheme, typography = typography) { ProvideTextStyle(typography.bodyLarge, content) }
    }
}
