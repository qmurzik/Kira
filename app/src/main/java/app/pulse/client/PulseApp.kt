package app.pulse.client

import android.app.Application
import app.pulse.client.di.AppContainer
import app.pulse.client.notifications.MessagesSyncWorker
import app.pulse.client.notifications.Notifier
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PulseApp : Application(), ImageLoaderFactory {

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

    /** One image loader for the whole app, sharing OkHttp's connection pool with the API client. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { container.http }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(150L * 1024 * 1024)
                .build()
        }
        // VK CDN sends short cache headers; images are immutable by URL, so cache them anyway.
        .respectCacheHeaders(false)
        .crossfade(120)
        .build()
}
