package travel

import androidx.compose.runtime.Composable
@Composable actual fun rememberRouteMotionEnabled(): Boolean = System.getProperty("travel.routes.motion", "true").toBoolean()
