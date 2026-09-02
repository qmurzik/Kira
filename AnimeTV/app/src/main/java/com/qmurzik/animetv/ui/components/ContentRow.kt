package com.qmurzik.animetv.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qmurzik.animetv.domain.model.AnimeSummary

/** Horizontal safe-margin used everywhere so content never sits under a TV's overscan edge. */
val TvSafeHorizontalPadding = 48.dp

/**
 * One "Continue Watching" / "Trending" / ... shelf. [focusGroup] is what makes Left/Right
 * inside the row and Up/Down between rows both feel natural to a D-pad instead of Compose's
 * default focus-search occasionally jumping to an unrelated element (item 28).
 */
@Composable
fun ContentRow(
    title: String,
    items: List<AnimeSummary>,
    modifier: Modifier = Modifier,
    onItemClick: (AnimeSummary) -> Unit,
    progressFor: (AnimeSummary) -> Float? = { null },
) {
    if (items.isEmpty()) return

    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = TvSafeHorizontalPadding, vertical = 8.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = TvSafeHorizontalPadding),
            modifier = Modifier.focusGroup(),
        ) {
            items(items, key = { it.id }) { anime ->
                PosterCard(
                    title = anime.title,
                    posterUrl = anime.posterUrl,
                    subtitle = anime.year?.toString(),
                    rating = anime.rating,
                    progressFraction = progressFor(anime),
                    onClick = { onItemClick(anime) },
                )
            }
        }
    }
}
