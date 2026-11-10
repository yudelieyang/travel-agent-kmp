package travel
import androidx.compose.runtime.Composable
@Composable actual fun rememberContourMotionEnabled(): Boolean = System.getProperty("travel.background.motion", "true").toBoolean()
