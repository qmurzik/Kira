package com.qmurzik.animetv.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.qmurzik.animetv.R
import com.qmurzik.animetv.domain.model.AnimeSummary
import com.qmurzik.animetv.ui.components.ErrorState
import com.qmurzik.animetv.ui.components.LoadingState
import com.qmurzik.animetv.ui.components.PosterCard
import com.qmurzik.animetv.ui.components.TvSafeHorizontalPadding
import com.qmurzik.animetv.ui.home.errorMessage
import com.qmurzik.animetv.ui.theme.TvBackground
import com.qmurzik.animetv.ui.theme.TvSurfaceVariant

@Composable
fun SearchScreen(
    onOpenDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val recentQueries by viewModel.recentQueries.collectAsState()
    val fieldFocusRequester = remember(viewModel) { FocusRequester() }

    LaunchedEffect(Unit) { fieldFocusRequester.requestFocus() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = TvSafeHorizontalPadding, vertical = 32.dp),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .focusRequester(fieldFocusRequester),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.onSubmit() }),
        )

        Box(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
            when {
                state.isLoading -> LoadingState()
                state.error != null && state.results.isEmpty() -> ErrorState(
                    message = errorMessage(state.error!!),
                    onRetry = viewModel::onSubmit,
                )
                state.query.isBlank() -> RecentSearches(
                    queries = recentQueries,
                    onSelect = { viewModel.onQueryChange(it); viewModel.onSubmit() },
                    onClear = viewModel::clearRecentSearches,
                )
                state.results.isEmpty() -> Text(
                    text = stringResource(R.string.search_no_results),
                    style = MaterialTheme.typography.bodyLarge,
                )
                else -> SearchResultsGrid(results = state.results, onOpenDetails = onOpenDetails)
            }
        }
    }
}

@Composable
private fun SearchResultsGrid(results: List<AnimeSummary>, onOpenDetails: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 148.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        modifier = Modifier.focusGroup(),
    ) {
        items(results, key = { it.id }) { anime ->
            PosterCard(
                title = anime.title,
                posterUrl = anime.posterUrl,
                subtitle = anime.year?.toString(),
                rating = anime.rating,
                onClick = { onOpenDetails(anime.id) },
            )
        }
    }
}

@Composable
private fun RecentSearches(queries: List<String>, onSelect: (String) -> Unit, onClear: () -> Unit) {
    if (queries.isEmpty()) return
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.search_recent), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.action_clear),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable(onClick = onClear),
            )
        }
        Column(modifier = Modifier.padding(top = 12.dp).focusGroup()) {
            queries.forEach { query ->
                Text(
                    text = query,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(query) }
                        .background(TvSurfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}
