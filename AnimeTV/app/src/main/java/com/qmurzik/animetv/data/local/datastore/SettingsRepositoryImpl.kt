package com.qmurzik.animetv.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.qmurzik.animetv.domain.repository.AppLanguage
import com.qmurzik.animetv.domain.repository.AppSettings
import com.qmurzik.animetv.domain.repository.AppTheme
import com.qmurzik.animetv.domain.repository.AppearanceSettings
import com.qmurzik.animetv.domain.repository.NetworkSettings
import com.qmurzik.animetv.domain.repository.PlaybackSettings
import com.qmurzik.animetv.domain.repository.SettingsRepository
import com.qmurzik.animetv.domain.repository.VideoQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private object Keys {
    val QUALITY = stringPreferencesKey("playback_quality")
    val AUTOPLAY_NEXT = booleanPreferencesKey("autoplay_next")
    val SKIP_OPENING = booleanPreferencesKey("skip_opening")
    val SKIP_ENDING = booleanPreferencesKey("skip_ending")
    val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
    val PREFERRED_SOURCE = stringPreferencesKey("preferred_source")
    val PREFERRED_AUDIO_LANG = stringPreferencesKey("preferred_audio_lang")
    val PREFERRED_SUBTITLE_LANG = stringPreferencesKey("preferred_subtitle_lang")

    val THEME = stringPreferencesKey("theme")
    val TEXT_SCALE = floatPreferencesKey("text_scale")
    val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
    val LANGUAGE = stringPreferencesKey("language")

    val WIFI_ONLY = booleanPreferencesKey("wifi_only")
    val CACHE_LIMIT_MB = intPreferencesKey("cache_limit_mb")

    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs -> prefs.toAppSettings() }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = settings.first()
        val next = transform(current)
        dataStore.edit { prefs ->
            prefs[Keys.QUALITY] = next.playback.preferredQuality.name
            prefs[Keys.AUTOPLAY_NEXT] = next.playback.autoPlayNext
            prefs[Keys.SKIP_OPENING] = next.playback.skipOpening
            prefs[Keys.SKIP_ENDING] = next.playback.skipEnding
            prefs[Keys.PLAYBACK_SPEED] = next.playback.playbackSpeed
            next.playback.preferredSourceId?.let { prefs[Keys.PREFERRED_SOURCE] = it }
            next.playback.preferredAudioLanguage?.let { prefs[Keys.PREFERRED_AUDIO_LANG] = it }
            next.playback.preferredSubtitleLanguage?.let { prefs[Keys.PREFERRED_SUBTITLE_LANG] = it }

            prefs[Keys.THEME] = next.appearance.theme.name
            prefs[Keys.TEXT_SCALE] = next.appearance.textScale
            prefs[Keys.ANIMATIONS_ENABLED] = next.appearance.animationsEnabled
            prefs[Keys.LANGUAGE] = next.appearance.language.name

            prefs[Keys.WIFI_ONLY] = next.network.wifiOnly
            prefs[Keys.CACHE_LIMIT_MB] = next.network.cacheLimitMb

            prefs[Keys.ONBOARDING_COMPLETED] = next.onboardingCompleted
        }
    }
}

private fun Preferences.toAppSettings(): AppSettings = AppSettings(
    playback = PlaybackSettings(
        preferredQuality = this[Keys.QUALITY]?.let { runCatching { VideoQuality.valueOf(it) }.getOrNull() }
            ?: VideoQuality.AUTO,
        autoPlayNext = this[Keys.AUTOPLAY_NEXT] ?: true,
        skipOpening = this[Keys.SKIP_OPENING] ?: true,
        skipEnding = this[Keys.SKIP_ENDING] ?: false,
        playbackSpeed = this[Keys.PLAYBACK_SPEED] ?: 1.0f,
        preferredSourceId = this[Keys.PREFERRED_SOURCE],
        preferredAudioLanguage = this[Keys.PREFERRED_AUDIO_LANG],
        preferredSubtitleLanguage = this[Keys.PREFERRED_SUBTITLE_LANG],
    ),
    appearance = AppearanceSettings(
        theme = this[Keys.THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.DARK,
        textScale = this[Keys.TEXT_SCALE] ?: 1.0f,
        animationsEnabled = this[Keys.ANIMATIONS_ENABLED] ?: true,
        language = this[Keys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
            ?: AppLanguage.SYSTEM,
    ),
    network = NetworkSettings(
        wifiOnly = this[Keys.WIFI_ONLY] ?: false,
        cacheLimitMb = this[Keys.CACHE_LIMIT_MB] ?: 512,
    ),
    onboardingCompleted = this[Keys.ONBOARDING_COMPLETED] ?: false,
)
