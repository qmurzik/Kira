package com.qmurzik.animetv.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import com.qmurzik.animetv.R
import com.qmurzik.animetv.domain.repository.VideoQuality
import com.qmurzik.animetv.ui.components.ErrorState
import com.qmurzik.animetv.ui.components.LoadingState
import com.qmurzik.animetv.ui.components.TvButton
import com.qmurzik.animetv.ui.home.errorMessage
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var controlsVisible by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LaunchedEffect(controlsVisible, state.isPlaying) {
        if (controlsVisible && state.isPlaying) {
            delay(5_000)
            controlsVisible = false
        }
    }

    BackHandler {
        if (controlsVisible) onBack() else controlsVisible = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                when (event.key) {
                    Key.Back -> false
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (!controlsVisible) controlsVisible = true else viewModel.togglePlayPause()
                        true
                    }
                    Key.DirectionLeft -> {
                        if (controlsVisible) viewModel.seekBy(-10_000) else controlsVisible = true
                        true
                    }
                    Key.DirectionRight -> {
                        if (controlsVisible) viewModel.seekBy(10_000) else controlsVisible = true
                        true
                    }
                    Key.MediaPlayPause -> { viewModel.togglePlayPause(); true }
                    Key.MediaFastForward -> { viewModel.seekBy(30_000); true }
                    Key.MediaRewind -> { viewModel.seekBy(-30_000); true }
                    Key.DirectionUp, Key.DirectionDown -> {
                        controlsVisible = true
                        true
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = viewModel.player
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (state.isLoading) LoadingState()

        state.error?.let { error ->
            ErrorState(
                message = errorMessage(error),
                onRetry = { viewModel.loadEpisode(state.seasonNumber, state.episodeNumber) },
                onBack = onBack,
            )
        }

        AnimatedVisibility(visible = controlsVisible && state.error == null) {
            PlayerControlsOverlay(state = state, viewModel = viewModel)
        }

        if (state.isDemoContent) {
            DemoBadge(providerLabel = state.providerLabel, modifier = Modifier.align(Alignment.TopEnd))
        }

        state.autoplayCountdownSeconds?.let { seconds ->
            AutoplayCountdownOverlay(
                seconds = seconds,
                onCancel = viewModel::cancelAutoplayCountdown,
                onPlayNow = viewModel::playNext,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
private fun PlayerControlsOverlay(state: PlayerUiState, viewModel: PlayerViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        Column {
            Text(text = state.animeTitle, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(
                text = stringResource(R.string.player_episode_label, state.seasonNumber, state.episodeNumber) +
                    (state.episodeTitle?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Start).fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs) else 0f
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(state.positionMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
                Text(formatTime(state.durationMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }

            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.hasPreviousEpisode) {
                    TvButton(text = "", icon = Icons.Filled.SkipPrevious, onClick = viewModel::playPrevious)
                }
                TvButton(text = "", icon = Icons.Filled.Replay10, onClick = { viewModel.seekBy(-10_000) })
                TvButton(
                    text = "",
                    icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    primary = true,
                    onClick = viewModel::togglePlayPause,
                )
                TvButton(text = "", icon = Icons.Filled.Forward10, onClick = { viewModel.seekBy(10_000) })
                if (state.hasNextEpisode) {
                    TvButton(text = "", icon = Icons.Filled.SkipNext, onClick = viewModel::playNext)
                }
                TvButton(
                    text = "${state.playbackSpeed}x",
                    icon = Icons.Filled.Speed,
                    onClick = { viewModel.setPlaybackSpeed(nextSpeed(state.playbackSpeed)) },
                )
                TvButton(text = "", icon = Icons.Filled.Tune, onClick = { viewModel.setQuality(nextQuality(state.quality)) })
                if (state.audioTrackCount > 1) {
                    TvButton(text = "", icon = Icons.Filled.GraphicEq, onClick = viewModel::cycleAudioTrack)
                }
                if (state.subtitleTrackCount > 0) {
                    TvButton(text = "", icon = Icons.Filled.Subtitles, onClick = viewModel::cycleSubtitleTrack)
                }
            }
        }
    }
}

@Composable
private fun DemoBadge(providerLabel: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = stringResource(R.string.player_demo_badge, providerLabel),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}

@Composable
private fun AutoplayCountdownOverlay(
    seconds: Int,
    onCancel: () -> Unit,
    onPlayNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(R.string.player_next_episode_in, seconds),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(text = stringResource(R.string.action_play_now), onClick = onPlayNow, primary = true)
            TvButton(text = stringResource(R.string.action_cancel), onClick = onCancel)
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private fun nextSpeed(current: Float): Float {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    val index = speeds.indexOfFirst { it == current }.takeIf { it >= 0 } ?: 2
    return speeds[(index + 1) % speeds.size]
}

private fun nextQuality(current: VideoQuality): VideoQuality =
    VideoQuality.entries[(VideoQuality.entries.indexOf(current) + 1) % VideoQuality.entries.size]
