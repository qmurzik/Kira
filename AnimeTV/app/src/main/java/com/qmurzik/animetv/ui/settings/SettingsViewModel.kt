package com.qmurzik.animetv.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.qmurzik.animetv.data.local.db.AnimeIndexDao
import com.qmurzik.animetv.domain.repository.AppLanguage
import com.qmurzik.animetv.domain.repository.AppSettings
import com.qmurzik.animetv.domain.repository.AppTheme
import com.qmurzik.animetv.domain.repository.HistoryRepository
import com.qmurzik.animetv.domain.repository.SettingsRepository
import com.qmurzik.animetv.domain.repository.VideoQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val animeIndexDao: AnimeIndexDao,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setQuality(quality: VideoQuality) = update { it.copy(playback = it.playback.copy(preferredQuality = quality)) }
    fun setAutoPlayNext(enabled: Boolean) = update { it.copy(playback = it.playback.copy(autoPlayNext = enabled)) }
    fun setSkipOpening(enabled: Boolean) = update { it.copy(playback = it.playback.copy(skipOpening = enabled)) }
    fun setSkipEnding(enabled: Boolean) = update { it.copy(playback = it.playback.copy(skipEnding = enabled)) }
    fun setPreferredSource(sourceId: String?) = update { it.copy(playback = it.playback.copy(preferredSourceId = sourceId)) }

    fun setTheme(theme: AppTheme) = update { it.copy(appearance = it.appearance.copy(theme = theme)) }
    fun setTextScale(scale: Float) = update { it.copy(appearance = it.appearance.copy(textScale = scale)) }
    fun setAnimationsEnabled(enabled: Boolean) = update { it.copy(appearance = it.appearance.copy(animationsEnabled = enabled)) }
    fun setLanguage(language: AppLanguage) = update { it.copy(appearance = it.appearance.copy(language = language)) }

    fun setWifiOnly(enabled: Boolean) = update { it.copy(network = it.network.copy(wifiOnly = enabled)) }
    fun setCacheLimitMb(limit: Int) = update { it.copy(network = it.network.copy(cacheLimitMb = limit)) }

    fun clearImageCache() {
        context.imageLoader.memoryCache?.clear()
        context.imageLoader.diskCache?.clear()
    }

    fun clearHistory() {
        viewModelScope.launch { historyRepository.clearHistory() }
    }

    fun clearMetadataCache() {
        viewModelScope.launch { animeIndexDao.clearAll() }
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }
}
