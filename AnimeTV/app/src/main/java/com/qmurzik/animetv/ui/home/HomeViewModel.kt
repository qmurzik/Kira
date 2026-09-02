package com.qmurzik.animetv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qmurzik.animetv.domain.model.AnimeDetails
import com.qmurzik.animetv.domain.model.AnimeSummary
import com.qmurzik.animetv.domain.repository.AnimeOutcome
import com.qmurzik.animetv.domain.repository.AnimeRepository
import com.qmurzik.animetv.domain.repository.HistoryRepository
import com.qmurzik.animetv.domain.repository.WatchProgress
import com.qmurzik.animetv.domain.source.SourceError
import com.qmurzik.animetv.util.NetworkConnectivityObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val error: SourceError? = null,
    val isOffline: Boolean = false,
    val featured: AnimeDetails? = null,
    val continueWatching: List<WatchProgress> = emptyList(),
    val trending: List<AnimeSummary> = emptyList(),
    val popular: List<AnimeSummary> = emptyList(),
    val recentlyAdded: List<AnimeSummary> = emptyList(),
    val recommended: List<AnimeSummary> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val animeRepository: AnimeRepository,
    private val historyRepository: HistoryRepository,
    connectivityObserver: NetworkConnectivityObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            historyRepository.observeContinueWatching().collectLatest { list ->
                _uiState.value = _uiState.value.copy(continueWatching = list)
            }
        }
        viewModelScope.launch {
            connectivityObserver.isOnline().collectLatest { online ->
                _uiState.value = _uiState.value.copy(isOffline = !online)
                if (online && _uiState.value.error == SourceError.NoConnectivity) {
                    load()
                }
            }
        }
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val outcome = animeRepository.homeSections()) {
                is AnimeOutcome.Success -> {
                    val sections = outcome.value
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        trending = sections.trending,
                        popular = sections.popular,
                        recentlyAdded = sections.recentlyAdded,
                        recommended = sections.recommended,
                    )
                    sections.trending.firstOrNull()?.let { loadFeatured(it.id) }
                }
                is AnimeOutcome.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = outcome.reason)
                }
            }
        }
    }

    private fun loadFeatured(animeId: String) {
        viewModelScope.launch {
            val outcome = animeRepository.getDetails(animeId)
            if (outcome is AnimeOutcome.Success) {
                _uiState.value = _uiState.value.copy(featured = outcome.value)
            }
        }
    }
}
