package com.qmurzik.animetv.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.qmurzik.animetv.R
import com.qmurzik.animetv.domain.repository.WatchProgress
import com.qmurzik.animetv.ui.components.TvButton
import com.qmurzik.animetv.ui.components.TvSafeHorizontalPadding
import com.qmurzik.animetv.ui.theme.TvBackground
import com.qmurzik.animetv.ui.theme.TvPrimary
import com.qmurzik.animetv.ui.theme.TvSurfaceVariant

@Composable
fun HistoryScreen(
    onResume: (animeId: String, season: Int, episode: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = TvSafeHorizontalPadding, vertical = 32.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.nav_history), style = MaterialTheme.typography.headlineMedium)
            if (history.isNotEmpty()) {
                TvButton(text = stringResource(R.string.action_clear_history), onClick = viewModel::clearHistory)
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
            if (history.isEmpty()) {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.focusGroup(),
                ) {
                    items(history, key = { "${it.animeId}_${it.seasonNumber}_${it.episodeNumber}" }) { entry ->
                        HistoryRow(
                            progress = entry,
                            onClick = { onResume(entry.animeId, entry.seasonNumber, entry.episodeNumber) },
                            onRemove = { viewModel.removeEntry(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(progress: WatchProgress, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TvSurfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) {
            AsyncImage(
                model = progress.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.fractionWatched)
                        .background(TvPrimary)
                        .width(3.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(text = progress.animeTitle, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(
                    R.string.continue_watching_subtitle,
                    progress.seasonNumber,
                    progress.episodeNumber,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_remove))
        }
    }
}
