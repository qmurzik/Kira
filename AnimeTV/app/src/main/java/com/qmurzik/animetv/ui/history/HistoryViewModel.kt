package com.qmurzik.animetv.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qmurzik.animetv.domain.repository.HistoryRepository
import com.qmurzik.animetv.domain.repository.WatchProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    val history: StateFlow<List<WatchProgress>> = historyRepository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearHistory() {
        viewModelScope.launch { historyRepository.clearHistory() }
    }

    fun removeEntry(progress: WatchProgress) {
        viewModelScope.launch {
            historyRepository.removeEntry(progress.animeId, progress.seasonNumber, progress.episodeNumber)
        }
    }
}
