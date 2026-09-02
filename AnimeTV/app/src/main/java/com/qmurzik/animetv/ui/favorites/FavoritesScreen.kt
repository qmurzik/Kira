package com.qmurzik.animetv.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.qmurzik.animetv.R
import com.qmurzik.animetv.domain.repository.FavoritesSortOrder
import com.qmurzik.animetv.ui.components.PosterCard
import com.qmurzik.animetv.ui.components.TvButton
import com.qmurzik.animetv.ui.components.TvSafeHorizontalPadding
import com.qmurzik.animetv.ui.theme.TvBackground

@Composable
fun FavoritesScreen(
    onOpenDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = TvSafeHorizontalPadding, vertical = 32.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.nav_favorites), style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortButton(FavoritesSortOrder.RECENTLY_ADDED, state.sort, R.string.sort_recent, viewModel::setSort)
                SortButton(FavoritesSortOrder.TITLE_AZ, state.sort, R.string.sort_title, viewModel::setSort)
                SortButton(FavoritesSortOrder.YEAR, state.sort, R.string.sort_year, viewModel::setSort)
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
            if (state.items.isEmpty()) {
                Text(
                    text = stringResource(R.string.favorites_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 148.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.focusGroup(),
                ) {
                    items(state.items, key = { it.anime.id }) { item ->
                        PosterCard(
                            title = item.anime.title,
                            posterUrl = item.anime.posterUrl,
                            subtitle = item.anime.year?.toString(),
                            rating = item.anime.rating,
                            onClick = { onOpenDetails(item.anime.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortButton(
    order: FavoritesSortOrder,
    current: FavoritesSortOrder,
    labelRes: Int,
    onClick: (FavoritesSortOrder) -> Unit,
) {
    TvButton(text = stringResource(labelRes), primary = order == current, onClick = { onClick(order) })
}
