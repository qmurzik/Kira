package com.qmurzik.animetv.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qmurzik.animetv.domain.model.AnimeDetails
import com.qmurzik.animetv.domain.model.Season
import com.qmurzik.animetv.domain.repository.AnimeOutcome
import com.qmurzik.animetv.domain.repository.AnimeRepository
import com.qmurzik.animetv.domain.repository.FavoritesRepository
import com.qmurzik.animetv.domain.repository.HistoryRepository
import com.qmurzik.animetv.domain.repository.WatchProgress
import com.qmurzik.animetv.domain.source.SourceError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

data class DetailsUiState(
    val isLoading: Boolean = true,
    val error: SourceError? = null,
    val details: AnimeDetails? = null,
    val seasons: List<Season> = emptyList(),
    val isFavorite: Boolean = false,
    val progressByEpisode: Map<String, WatchProgress> = emptyMap(),
)

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val animeRepository: AnimeRepository,
    private val favoritesRepository: FavoritesRepository,
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    val animeId: String = URLDecoder.decode(checkNotNull(savedStateHandle.get<String>("animeId")), "UTF-8")

    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = combine(
        _uiState,
        favoritesRepository.isFavorite(animeId),
        historyRepository.observeHistory(),
    ) { state, favorite, history ->
        state.copy(
            isFavorite = favorite,
            progressByEpisode = history
                .filter { it.animeId == animeId }
                .associateBy { "${it.seasonNumber}_${it.episodeNumber}" },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailsUiState())

    init {
        load()
    }

    fun retry() = load()

    fun toggleFavorite() {
        val details = _uiState.value.details ?: return
        viewModelScope.launch { favoritesRepository.toggle(details.toSummary()) }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val outcome = animeRepository.getDetails(animeId)) {
                is AnimeOutcome.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, details = outcome.value)
                    loadSeasons()
                }
                is AnimeOutcome.Error -> _uiState.value = _uiState.value.copy(isLoading = false, error = outcome.reason)
            }
        }
    }

    private fun loadSeasons() {
        viewModelScope.launch {
            when (val outcome = animeRepository.getSeasons(animeId)) {
                is AnimeOutcome.Success -> _uiState.value = _uiState.value.copy(seasons = outcome.value)
                is AnimeOutcome.Error -> Unit // Details already loaded; episode list is best-effort.
            }
        }
    }
}
