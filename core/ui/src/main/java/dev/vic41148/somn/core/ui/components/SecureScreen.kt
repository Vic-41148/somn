package dev.vic41148.somn.core.ui.components

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Marks the hosting window secure while this composable is in the composition: blocks
 * screenshots and screen recording, and — the more common real-world leak — blanks the
 * app-switcher thumbnail. Use on screens showing sleep-talk clips, cycle data, or the
 * recovery key; never app-wide, so legitimate score sharing keeps working.
 */
@Composable
fun SecureScreen() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
