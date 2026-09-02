package com.qmurzik.animetv.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qmurzik.animetv.domain.repository.FavoriteItem
import com.qmurzik.animetv.domain.repository.FavoritesRepository
import com.qmurzik.animetv.domain.repository.FavoritesSortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class FavoritesUiState(
    val items: List<FavoriteItem> = emptyList(),
    val sort: FavoritesSortOrder = FavoritesSortOrder.RECENTLY_ADDED,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
) : ViewModel() {

    private val sort = MutableStateFlow(FavoritesSortOrder.RECENTLY_ADDED)

    val uiState: StateFlow<FavoritesUiState> = sort
        .flatMapLatest { order ->
            favoritesRepository.observeFavorites(order).map { items -> FavoritesUiState(items, order) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState())

    fun setSort(order: FavoritesSortOrder) {
        sort.value = order
    }
}
