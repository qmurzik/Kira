package com.qmurzik.animetv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qmurzik.animetv.domain.model.AnimeSummary
import com.qmurzik.animetv.domain.repository.AnimeOutcome
import com.qmurzik.animetv.domain.repository.AnimeRepository
import com.qmurzik.animetv.domain.repository.SearchHistoryRepository
import com.qmurzik.animetv.domain.source.SourceError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DEBOUNCE_MS = 400L

enum class SearchSort { RELEVANCE, TITLE_AZ, YEAR_DESC, RATING_DESC }

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val error: SourceError? = null,
    val results: List<AnimeSummary> = emptyList(),
    val sort: SearchSort = SearchSort.RELEVANCE,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val animeRepository: AnimeRepository,
    private val searchHistoryRepository: SearchHistoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val recentQueries: StateFlow<List<String>> = searchHistoryRepository.observeRecentQueries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        // Cancelling the in-flight job on every keystroke is what gives us both the debounce
        // and "don't let a stale request's results clobber a newer one" (item 18).
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(results = emptyList(), isLoading = false, error = null)
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            runSearch(query)
        }
    }

    fun onSort(sort: SearchSort) {
        _uiState.value = _uiState.value.copy(sort = sort, results = sortResults(_uiState.value.results, sort))
    }

    fun onSubmit() {
        val query = _uiState.value.query
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(query) }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { searchHistoryRepository.clear() }
    }

    private suspend fun runSearch(query: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        when (val outcome = animeRepository.search(query)) {
            is AnimeOutcome.Success -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    results = sortResults(outcome.value, _uiState.value.sort),
                )
                if (outcome.value.isNotEmpty()) searchHistoryRepository.record(query)
            }
            is AnimeOutcome.Error -> _uiState.value = _uiState.value.copy(isLoading = false, error = outcome.reason)
        }
    }

    private fun sortResults(results: List<AnimeSummary>, sort: SearchSort): List<AnimeSummary> = when (sort) {
        SearchSort.RELEVANCE -> results
        SearchSort.TITLE_AZ -> results.sortedBy { it.title.lowercase() }
        SearchSort.YEAR_DESC -> results.sortedByDescending { it.year ?: 0 }
        SearchSort.RATING_DESC -> results.sortedByDescending { it.rating ?: 0.0 }
    }
}
