package app.pulse.client

import android.app.Application
import app.pulse.client.di.AppContainer
import app.pulse.client.notifications.MessagesSyncWorker
import app.pulse.client.notifications.Notifier
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PulseApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannels(this)
        // Keep the background message check in sync with settings (off-main, cheap).
        container.appScope.launch {
            container.settings.settings
                .map { it.messageNotifications to it.syncIntervalMinutes }
                .distinctUntilChanged()
                .collect { (enabled, minutes) ->
                    MessagesSyncWorker.schedule(this@PulseApp, minutes, enabled)
                }
        }
    }
}
