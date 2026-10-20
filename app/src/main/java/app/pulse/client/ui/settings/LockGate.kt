package app.pulse.client.ui.settings

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pulse.client.data.settings.AppSettings
import app.pulse.client.ui.LocalAppContainer

/**
 * Applies FLAG_SECURE and draws the PIN screen over the app while it is locked.
 * The app content stays composed underneath so navigation state survives locking.
 */
@Composable
fun LockGate(settings: AppSettings, content: @Composable () -> Unit) {
    val container = LocalAppContainer.current
    val activity = LocalContext.current as? Activity
    val locked by container.appLock.locked.collectAsStateWithLifecycle()

    DisposableEffect(settings.secureScreen, activity) {
        val window = activity?.window
        if (settings.secureScreen) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { }
    }

    Box(Modifier.fillMaxSize()) {
        content()
        if (locked) {
            BackHandler { activity?.moveTaskToBack(true) }
            LockScreen(settings.biometricEnabled)
        }
    }
}
