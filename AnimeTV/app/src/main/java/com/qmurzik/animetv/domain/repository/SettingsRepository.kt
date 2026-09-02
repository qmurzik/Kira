package com.qmurzik.animetv.domain.repository

import kotlinx.coroutines.flow.Flow

enum class AppTheme { DARK, DARKER_AMOLED }
enum class VideoQuality { AUTO, HIGH, MEDIUM, LOW }
enum class AppLanguage { SYSTEM, ENGLISH, RUSSIAN }

data class PlaybackSettings(
    val preferredQuality: VideoQuality = VideoQuality.AUTO,
    val autoPlayNext: Boolean = true,
    val skipOpening: Boolean = true,
    val skipEnding: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val preferredSourceId: String? = null,
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
)

data class AppearanceSettings(
    val theme: AppTheme = AppTheme.DARK,
    val textScale: Float = 1.0f,
    val animationsEnabled: Boolean = true,
    val language: AppLanguage = AppLanguage.SYSTEM,
)

data class NetworkSettings(
    val wifiOnly: Boolean = false,
    val cacheLimitMb: Int = 512,
)

data class AppSettings(
    val playback: PlaybackSettings = PlaybackSettings(),
    val appearance: AppearanceSettings = AppearanceSettings(),
    val network: NetworkSettings = NetworkSettings(),
    val onboardingCompleted: Boolean = false,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
