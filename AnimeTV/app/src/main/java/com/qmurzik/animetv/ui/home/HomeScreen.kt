package com.qmurzik.animetv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.qmurzik.animetv.R
import com.qmurzik.animetv.domain.repository.WatchProgress
import com.qmurzik.animetv.domain.source.SourceError
import com.qmurzik.animetv.ui.components.ContentRow
import com.qmurzik.animetv.ui.components.ErrorState
import com.qmurzik.animetv.ui.components.HeroBanner
import com.qmurzik.animetv.ui.components.LoadingState
import com.qmurzik.animetv.ui.components.PosterCard
import com.qmurzik.animetv.ui.components.TvSafeHorizontalPadding
import com.qmurzik.animetv.ui.theme.TvBackground

@Composable
fun HomeScreen(
    onOpenDetails: (String) -> Unit,
    onPlay: (animeId: String, season: Int, episode: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    when {
        state.isLoading && state.trending.isEmpty() -> LoadingState(modifier = modifier.background(TvBackground))
        state.error != null && state.trending.isEmpty() -> ErrorState(
            modifier = modifier.background(TvBackground),
            message = errorMessage(state.error!!),
            offline = state.error == SourceError.NoConnectivity,
            onRetry = viewModel::retry,
        )
        else -> LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(TvBackground),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            state.featured?.let { featured ->
                item {
                    HeroBanner(
                        anime = featured,
                        onWatch = { onPlay(featured.id, 1, 1) },
                        onDetails = { onOpenDetails(featured.id) },
                    )
                }
            }

            if (state.continueWatching.isNotEmpty()) {
                item {
                    ContinueWatchingRow(
                        items = state.continueWatching,
                        onClick = { onPlay(it.animeId, it.seasonNumber, it.episodeNumber) },
                    )
                }
            }

            item {
                ContentRow(
                    title = stringResource(R.string.home_trending),
                    items = state.trending,
                    onItemClick = { onOpenDetails(it.id) },
                )
            }
            item {
                ContentRow(
                    title = stringResource(R.string.home_popular),
                    items = state.popular,
                    onItemClick = { onOpenDetails(it.id) },
                )
            }
            item {
                ContentRow(
                    title = stringResource(R.string.home_recently_added),
                    items = state.recentlyAdded,
                    onItemClick = { onOpenDetails(it.id) },
                )
            }
            item {
                ContentRow(
                    title = stringResource(R.string.home_recommended),
                    items = state.recommended,
                    onItemClick = { onOpenDetails(it.id) },
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(items: List<WatchProgress>, onClick: (WatchProgress) -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.home_continue_watching),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = TvSafeHorizontalPadding, vertical = 8.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = TvSafeHorizontalPadding),
            modifier = Modifier.focusGroup(),
        ) {
            items(items, key = { "${it.animeId}_${it.seasonNumber}_${it.episodeNumber}" }) { progress ->
                PosterCard(
                    title = progress.animeTitle,
                    posterUrl = progress.posterUrl,
                    subtitle = stringResource(
                        R.string.continue_watching_subtitle,
                        progress.seasonNumber,
                        progress.episodeNumber,
                    ),
                    progressFraction = progress.fractionWatched,
                    onClick = { onClick(progress) },
                )
            }
        }
    }
}

@Composable
fun errorMessage(error: SourceError): String = when (error) {
    SourceError.NoConnectivity -> stringResource(R.string.error_no_connectivity)
    SourceError.Timeout -> stringResource(R.string.error_timeout)
    SourceError.RateLimited -> stringResource(R.string.error_rate_limited)
    SourceError.NotFound -> stringResource(R.string.error_not_found)
    SourceError.Unsupported -> stringResource(R.string.error_unsupported)
    is SourceError.Http -> stringResource(R.string.error_http, error.code)
    is SourceError.Unknown -> stringResource(R.string.error_unknown)
}
