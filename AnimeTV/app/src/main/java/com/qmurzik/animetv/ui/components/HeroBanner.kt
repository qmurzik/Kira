package com.qmurzik.animetv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.qmurzik.animetv.R
import com.qmurzik.animetv.domain.model.AnimeDetails
import com.qmurzik.animetv.ui.theme.TvScrimEnd
import com.qmurzik.animetv.ui.theme.TvScrimStart

/** Full-bleed featured-title banner at the top of Home (item 3/26). */
@Composable
fun HeroBanner(
    anime: AnimeDetails,
    modifier: Modifier = Modifier,
    onWatch: () -> Unit,
    onDetails: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(21f / 9f),
    ) {
        AsyncImage(
            model = anime.backdropUrl ?: anime.posterUrl,
            contentDescription = anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(TvScrimStart, TvScrimEnd))),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = TvSafeHorizontalPadding, vertical = 32.dp)
                .widthIn(max = 640.dp),
        ) {
            Text(
                text = anime.title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!anime.synopsis.isNullOrBlank()) {
                Text(
                    text = anime.synopsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(modifier = Modifier.padding(top = 20.dp)) {
                TvButton(
                    text = stringResource(R.string.action_watch),
                    onClick = onWatch,
                    primary = true,
                    icon = Icons.Filled.PlayArrow,
                )
                Row(modifier = Modifier.padding(start = 16.dp)) {
                    TvButton(
                        text = stringResource(R.string.action_details),
                        onClick = onDetails,
                        icon = Icons.Filled.Info,
                    )
                }
            }
        }
    }
}
