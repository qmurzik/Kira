package com.qmurzik.animetv.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qmurzik.animetv.domain.repository.AppLanguage
import com.qmurzik.animetv.domain.repository.SettingsRepository
import com.qmurzik.animetv.domain.repository.VideoQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingStep { WELCOME, LANGUAGE, QUALITY, SOURCE }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val quality: VideoQuality = VideoQuality.AUTO,
    val preferredSourceId: String? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val steps = OnboardingStep.entries

    fun next(onFinished: () -> Unit) {
        val currentIndex = steps.indexOf(_uiState.value.step)
        if (currentIndex == steps.lastIndex) {
            finish(onFinished)
        } else {
            _uiState.value = _uiState.value.copy(step = steps[currentIndex + 1])
        }
    }

    fun setLanguage(language: AppLanguage) {
        _uiState.value = _uiState.value.copy(language = language)
    }

    fun setQuality(quality: VideoQuality) {
        _uiState.value = _uiState.value.copy(quality = quality)
    }

    fun setPreferredSource(sourceId: String?) {
        _uiState.value = _uiState.value.copy(preferredSourceId = sourceId)
    }

    fun skip(onFinished: () -> Unit) = finish(onFinished)

    private fun finish(onFinished: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            settingsRepository.update {
                it.copy(
                    playback = it.playback.copy(
                        preferredQuality = state.quality,
                        preferredSourceId = state.preferredSourceId,
                    ),
                    appearance = it.appearance.copy(language = state.language),
                    onboardingCompleted = true,
                )
            }
            onFinished()
        }
    }
}
