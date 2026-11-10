package travel

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

@Composable actual fun rememberContourMotionEnabled(): Boolean {
    val context = LocalContext.current
    val owner = context as? LifecycleOwner
    var resumed by remember(owner) { mutableStateOf(owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) }
    var animations by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, _ -> resumed = owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true }
        owner?.lifecycle?.addObserver(observer)
        val setting = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { animations = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, setting)
        onDispose { owner?.lifecycle?.removeObserver(observer); context.contentResolver.unregisterContentObserver(setting) }
    }
    return resumed && animations
}
