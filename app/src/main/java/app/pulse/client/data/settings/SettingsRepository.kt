package app.pulse.client.data.settings

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { System, Light, Dark }

/** Non-secret preferences. Exportable as JSON (PIN and tokens live in SecureStore, not here). */
@Immutable
@Serializable
data class AppSettings(
    val theme: ThemeMode = ThemeMode.System,
    val fontScale: Float = 1f,
    val sendReadReceipts: Boolean = true,
    val sendTyping: Boolean = true,
    val hideNotificationText: Boolean = false,
    val secureScreen: Boolean = false,
    val autoplayVideo: Boolean = true,
    val dataSaver: Boolean = false,
    val messageNotifications: Boolean = true,
    val syncIntervalMinutes: Int = 15,
    val pinEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val lockTimeoutSeconds: Int = 60,
)

class SettingsRepository(private val context: Context) {

    private object K {
        val theme = stringPreferencesKey("theme")
        val fontScale = floatPreferencesKey("font_scale")
        val readReceipts = booleanPreferencesKey("read_receipts")
        val typing = booleanPreferencesKey("typing")
        val hideText = booleanPreferencesKey("hide_notification_text")
        val secure = booleanPreferencesKey("secure_screen")
        val autoplay = booleanPreferencesKey("autoplay")
        val dataSaver = booleanPreferencesKey("data_saver")
        val msgNotif = booleanPreferencesKey("message_notifications")
        val syncInterval = intPreferencesKey("sync_interval")
        val pin = booleanPreferencesKey("pin_enabled")
        val bio = booleanPreferencesKey("biometric_enabled")
        val lockTimeout = intPreferencesKey("lock_timeout")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
            fontScale = p[K.fontScale] ?: 1f,
            sendReadReceipts = p[K.readReceipts] ?: true,
            sendTyping = p[K.typing] ?: true,
            hideNotificationText = p[K.hideText] ?: false,
            secureScreen = p[K.secure] ?: false,
            autoplayVideo = p[K.autoplay] ?: true,
            dataSaver = p[K.dataSaver] ?: false,
            messageNotifications = p[K.msgNotif] ?: true,
            syncIntervalMinutes = p[K.syncInterval] ?: 15,
            pinEnabled = p[K.pin] ?: false,
            biometricEnabled = p[K.bio] ?: false,
            lockTimeoutSeconds = p[K.lockTimeout] ?: 60,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(current())
        context.dataStore.edit { p ->
            p[K.theme] = next.theme.name
            p[K.fontScale] = next.fontScale.coerceIn(0.8f, 1.4f)
            p[K.readReceipts] = next.sendReadReceipts
            p[K.typing] = next.sendTyping
            p[K.hideText] = next.hideNotificationText
            p[K.secure] = next.secureScreen
            p[K.autoplay] = next.autoplayVideo
            p[K.dataSaver] = next.dataSaver
            p[K.msgNotif] = next.messageNotifications
            p[K.syncInterval] = next.syncIntervalMinutes.coerceAtLeast(15)
            p[K.pin] = next.pinEnabled
            p[K.bio] = next.biometricEnabled
            p[K.lockTimeout] = next.lockTimeoutSeconds
        }
    }
}
