package com.qmurzik.animetv.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.Intent
import android.net.Uri
import coil.compose.AsyncImage
import com.qmurzik.animetv.R
import com.qmurzik.animetv.domain.model.AnimeStatus
import com.qmurzik.animetv.domain.model.Episode
import com.qmurzik.animetv.domain.repository.WatchProgress
import com.qmurzik.animetv.ui.components.ErrorState
import com.qmurzik.animetv.ui.components.LoadingState
import com.qmurzik.animetv.ui.components.TvButton
import com.qmurzik.animetv.ui.components.TvSafeHorizontalPadding
import com.qmurzik.animetv.ui.home.errorMessage
import com.qmurzik.animetv.ui.theme.TvBackground
import com.qmurzik.animetv.ui.theme.TvPrimary
import com.qmurzik.animetv.ui.theme.TvScrimEnd
import com.qmurzik.animetv.ui.theme.TvScrimStart
import com.qmurzik.animetv.ui.theme.TvSurfaceVariant

@Composable
fun DetailsScreen(
    onPlay: (animeId: String, season: Int, episode: Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    when {
        state.isLoading && state.details == null -> LoadingState(modifier = modifier.background(TvBackground))
        state.error != null && state.details == null -> ErrorState(
            modifier = modifier.background(TvBackground),
            message = errorMessage(state.error!!),
            onRetry = viewModel::retry,
            onBack = onBack,
        )
        state.details != null -> DetailsContent(
            state = state,
            modifier = modifier,
            onPlay = { season, episode -> onPlay(viewModel.animeId, season, episode) },
            onToggleFavorite = viewModel::toggleFavorite,
        )
    }
}

@Composable
private fun DetailsContent(
    state: DetailsUiState,
    modifier: Modifier,
    onPlay: (Int, Int) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val details = requireNotNull(state.details)
    val context = LocalContext.current
    var selectedSeasonIndex by remember(details.id) { mutableIntStateOf(0) }
    val selectedSeason = state.seasons.getOrNull(selectedSeasonIndex)

    LazyColumn(
        modifier = modifier.fillMaxSize().background(TvBackground),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(21f / 9f)) {
                AsyncImage(
                    model = details.backdropUrl ?: details.posterUrl,
                    contentDescription = details.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(TvScrimStart, TvScrimEnd))),
                )
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = TvSafeHorizontalPadding, vertical = 24.dp)) {
                Text(
                    text = details.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (!details.titleNative.isNullOrBlank() && details.titleNative != details.title) {
                    Text(
                        text = details.titleNative,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(modifier = Modifier.padding(top = 8.dp)) {
                    val meta = listOfNotNull(
                        details.genres.take(3).joinToString(" · ").ifBlank { null },
                        details.year?.toString(),
                        stringResource(statusLabel(details.status)),
                        details.episodeCount?.let {
                            pluralStringResource(R.plurals.episodes_count, it, it)
                        },
                    )
                    Text(
                        text = meta.joinToString("  •  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!details.synopsis.isNullOrBlank()) {
                    Text(
                        text = details.synopsis,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp).widthIn(max = 900.dp),
                    )
                }

                Row(modifier = Modifier.padding(top = 20.dp)) {
                    TvButton(
                        text = stringResource(R.string.action_watch),
                        icon = Icons.Filled.PlayArrow,
                        primary = true,
                        onClick = {
                            val firstUnwatched = selectedSeason?.episodes?.firstOrNull()
                            onPlay(selectedSeason?.number ?: 1, firstUnwatched?.number ?: 1)
                        },
                    )
                    Row(modifier = Modifier.padding(start = 16.dp)) {
                        TvButton(
                            text = stringResource(
                                if (state.isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite,
                            ),
                            icon = if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            onClick = onToggleFavorite,
                        )
                    }
                }

                if (details.officialLinks.isNotEmpty()) {
                    Column(modifier = Modifier.padding(top = 24.dp)) {
                        Text(
                            text = stringResource(R.string.details_official_sources),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(modifier = Modifier.padding(top = 8.dp).focusGroup()) {
                            details.officialLinks.forEach { link ->
                                Row(modifier = Modifier.padding(end = 12.dp)) {
                                    TvButton(
                                        text = link.siteName,
                                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(link.url)),
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.seasons.size > 1) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = TvSafeHorizontalPadding, vertical = 8.dp).focusGroup(),
                ) {
                    state.seasons.forEachIndexed { index, season ->
                        Row(modifier = Modifier.padding(end = 12.dp)) {
                            TvButton(
                                text = season.name ?: stringResource(R.string.details_season, season.number),
                                primary = index == selectedSeasonIndex,
                                onClick = { selectedSeasonIndex = index },
                            )
                        }
                    }
                }
            }
        }

        selectedSeason?.let { season ->
            items(season.episodes, key = { it.number }) { episode ->
                EpisodeRow(
                    episode = episode,
                    progress = state.progressByEpisode["${season.number}_${episode.number}"],
                    onClick = { onPlay(season.number, episode.number) },
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, progress: WatchProgress?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TvSafeHorizontalPadding, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(TvSurfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(TvBackground),
        ) {
            if (episode.thumbnailUrl != null) {
                AsyncImage(
                    model = episode.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center),
            )
            if (progress != null && progress.fractionWatched > 0f) {
                Box(
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.fractionWatched)
                            .background(TvPrimary)
                            .width(2.dp),
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            val label = if (episode.title.isNullOrBlank()) {
                stringResource(R.string.details_episode_short, episode.number)
            } else {
                stringResource(R.string.details_episode_number, episode.number, episode.title)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (episode.durationSeconds != null) {
                Text(
                    text = stringResource(R.string.details_episode_duration, episode.durationSeconds / 60),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun statusLabel(status: AnimeStatus): Int = when (status) {
    AnimeStatus.AIRING -> R.string.status_airing
    AnimeStatus.FINISHED -> R.string.status_finished
    AnimeStatus.UPCOMING -> R.string.status_upcoming
    AnimeStatus.UNKNOWN -> R.string.status_unknown
}
