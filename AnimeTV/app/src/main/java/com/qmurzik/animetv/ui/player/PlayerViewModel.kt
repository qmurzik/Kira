package com.qmurzik.animetv.ui.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.qmurzik.animetv.domain.model.PlaybackSource
import com.qmurzik.animetv.domain.model.Season
import com.qmurzik.animetv.domain.repository.AnimeOutcome
import com.qmurzik.animetv.domain.repository.AnimeRepository
import com.qmurzik.animetv.domain.repository.HistoryRepository
import com.qmurzik.animetv.domain.repository.SettingsRepository
import com.qmurzik.animetv.domain.repository.VideoQuality
import com.qmurzik.animetv.domain.repository.WatchProgress
import com.qmurzik.animetv.domain.source.SourceError
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

data class PlayerUiState(
    val isLoading: Boolean = true,
    val error: SourceError? = null,
    val animeTitle: String = "",
    val posterUrl: String? = null,
    val seasonNumber: Int = 1,
    val episodeNumber: Int = 1,
    val episodeTitle: String? = null,
    val isDemoContent: Boolean = false,
    val providerLabel: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedPercentage: Int = 0,
    val playbackSpeed: Float = 1f,
    val quality: VideoQuality = VideoQuality.AUTO,
    val audioTrackCount: Int = 0,
    val subtitleTrackCount: Int = 0,
    val hasNextEpisode: Boolean = false,
    val hasPreviousEpisode: Boolean = false,
    val autoplayCountdownSeconds: Int? = null,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    savedStateHandle: SavedStateHandle,
    private val animeRepository: AnimeRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val animeId: String = URLDecoder.decode(checkNotNull(savedStateHandle.get<String>("animeId")), "UTF-8")

    // NavType.IntType (see Screen.Player's composable() arguments) hands these back as Int,
    // not String - unlike animeId, which is declared NavType.StringType.
    private val startSeason: Int = checkNotNull(savedStateHandle.get<Int>("season"))
    private val startEpisode: Int = checkNotNull(savedStateHandle.get<Int>("episode"))

    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var seasons: List<Season> = emptyList()
    private var progressSaveJob: Job? = null
    private var tickerJob: Job? = null
    private var autoplayJob: Job? = null
    private var currentPlaybackSource: PlaybackSource? = null

    // Declared before init{} - Kotlin runs property initializers and init blocks in source
    // order, and init{} below registers this listener, so it must already be assigned by then.
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) onEpisodeEnded()
            if (playbackState == Player.STATE_READY) {
                _uiState.value = _uiState.value.copy(isLoading = false, durationMs = player.duration.coerceAtLeast(0))
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = SourceError.Unknown(error.message))
        }

        override fun onTracksChanged(tracks: Tracks) {
            val audioCount = tracks.groups.count { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }
            val subtitleCount = tracks.groups.count { it.type == C.TRACK_TYPE_TEXT && it.length > 0 }
            _uiState.value = _uiState.value.copy(audioTrackCount = audioCount, subtitleTrackCount = subtitleCount)
        }
    }

    init {
        player.addListener(playerListener)
        startTicker()
        viewModelScope.launch {
            when (val outcome = animeRepository.getDetails(animeId)) {
                is AnimeOutcome.Success -> _uiState.value = _uiState.value.copy(
                    animeTitle = outcome.value.title,
                    posterUrl = outcome.value.posterUrl,
                )
                is AnimeOutcome.Error -> Unit // Non-fatal: the player still works, just without a title/poster.
            }
        }
        viewModelScope.launch {
            when (val outcome = animeRepository.getSeasons(animeId)) {
                is AnimeOutcome.Success -> seasons = outcome.value
                is AnimeOutcome.Error -> Unit // Non-fatal: next/previous just won't be offered.
            }
            updateEpisodeNavState()
        }
        loadEpisode(startSeason, startEpisode)
    }

    fun loadEpisode(seasonNumber: Int, episodeNumber: Int) {
        cancelAutoplayCountdown()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeTitle = episodeTitleFor(seasonNumber, episodeNumber),
                positionMs = 0,
                durationMs = 0,
            )
            when (val outcome = animeRepository.getStreams(animeId, seasonNumber, episodeNumber)) {
                is AnimeOutcome.Success -> preparePlayback(outcome.value)
                is AnimeOutcome.Error -> _uiState.value = _uiState.value.copy(isLoading = false, error = outcome.reason)
            }
            updateEpisodeNavState()
        }
    }

    private suspend fun preparePlayback(source: PlaybackSource) {
        currentPlaybackSource = source
        val variant = source.variants.firstOrNull()
        if (variant == null) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = SourceError.NotFound)
            return
        }

        val mediaItem = MediaItem.Builder()
            .setUri(variant.url)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .apply {
                source.subtitleTracks.firstOrNull { it.url != null }?.let { sub ->
                    setSubtitleConfigurations(
                        listOf(
                            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                                .setMimeType(sub.mimeType)
                                .setLanguage(sub.language)
                                .setLabel(sub.label)
                                .build(),
                        ),
                    )
                }
            }
            .build()

        val settings = settingsRepository.settings.first()
        applyQualityPreference(settings.playback.preferredQuality)
        player.setPlaybackSpeed(settings.playback.playbackSpeed)
        _uiState.value = _uiState.value.copy(
            isDemoContent = source.isDemoContent,
            providerLabel = source.providerLabel,
            playbackSpeed = settings.playback.playbackSpeed,
            quality = settings.playback.preferredQuality,
        )

        player.setMediaItem(mediaItem)
        player.prepare()

        val savedProgress = historyRepository.getProgress(
            animeId,
            _uiState.value.seasonNumber,
            _uiState.value.episodeNumber,
        )
        if (savedProgress != null && !savedProgress.isFinished) {
            player.seekTo(savedProgress.positionMs)
        }
        player.playWhenReady = true
    }

    fun togglePlayPause() {
        player.playWhenReady = !player.playWhenReady
    }

    fun seekBy(deltaMs: Long) {
        val target = (player.currentPosition + deltaMs).coerceIn(0, player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        player.seekTo(target)
    }

    fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
        viewModelScope.launch {
            settingsRepository.update { it.copy(playback = it.playback.copy(playbackSpeed = speed)) }
        }
    }

    fun setQuality(quality: VideoQuality) {
        applyQualityPreference(quality)
        _uiState.value = _uiState.value.copy(quality = quality)
        viewModelScope.launch {
            settingsRepository.update { it.copy(playback = it.playback.copy(preferredQuality = quality)) }
        }
    }

    private fun applyQualityPreference(quality: VideoQuality) {
        val maxBitrate = when (quality) {
            VideoQuality.AUTO -> Int.MAX_VALUE
            VideoQuality.HIGH -> 6_000_000
            VideoQuality.MEDIUM -> 2_500_000
            VideoQuality.LOW -> 800_000
        }
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setMaxVideoBitrate(maxBitrate)
            .build()
    }

    /** Cycles to the next available audio language and reports it via [uiState] (as a short
     *  toast-style label the screen can surface) - avoids leaking Media3's [Tracks] type into
     *  the Compose layer for what is, functionally, a "next track" button. */
    fun cycleAudioTrack() {
        val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }
        cycleTrackType(groups, C.TRACK_TYPE_AUDIO, allowNone = false)
    }

    fun cycleSubtitleTrack() {
        val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.length > 0 }
        cycleTrackType(groups, C.TRACK_TYPE_TEXT, allowNone = true)
    }

    private fun cycleTrackType(groups: List<Tracks.Group>, trackType: Int, allowNone: Boolean) {
        if (groups.isEmpty()) return
        val current = groups.firstOrNull { group -> (0 until group.length).any { group.isTrackSelected(it) } }
        val currentIndex = groups.indexOf(current)
        val nextIndex = currentIndex + 1

        val params = player.trackSelectionParameters.buildUpon()
        if (allowNone && nextIndex >= groups.size) {
            params.setTrackTypeDisabled(trackType, true)
        } else {
            val target = groups[nextIndex % groups.size]
            params.setTrackTypeDisabled(trackType, false)
            params.setOverrideForType(TrackSelectionOverride(target.mediaTrackGroup, 0))
        }
        player.trackSelectionParameters = params.build()
    }

    fun playNext() = adjacentEpisode(1)?.let { (s, e) -> loadEpisode(s, e) }
    fun playPrevious() = adjacentEpisode(-1)?.let { (s, e) -> loadEpisode(s, e) }

    fun cancelAutoplayCountdown() {
        autoplayJob?.cancel()
        autoplayJob = null
        _uiState.value = _uiState.value.copy(autoplayCountdownSeconds = null)
    }

    private fun onEpisodeEnded() {
        saveProgress(force = true)
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val next = adjacentEpisode(1)
            if (settings.playback.autoPlayNext && next != null) {
                startAutoplayCountdown(next.first, next.second)
            }
        }
    }

    private fun startAutoplayCountdown(nextSeason: Int, nextEpisode: Int) {
        autoplayJob?.cancel()
        autoplayJob = viewModelScope.launch {
            for (remaining in AUTOPLAY_COUNTDOWN_SECONDS downTo 1) {
                _uiState.value = _uiState.value.copy(autoplayCountdownSeconds = remaining)
                delay(1_000)
                if (!isActive) return@launch
            }
            _uiState.value = _uiState.value.copy(autoplayCountdownSeconds = null)
            loadEpisode(nextSeason, nextEpisode)
        }
    }

    private fun adjacentEpisode(direction: Int): Pair<Int, Int>? {
        val season = seasons.find { it.number == _uiState.value.seasonNumber } ?: return null
        val episodes = season.episodes.sortedBy { it.number }
        val currentIndex = episodes.indexOfFirst { it.number == _uiState.value.episodeNumber }
        if (currentIndex == -1) return null
        val targetIndex = currentIndex + direction
        return when {
            targetIndex in episodes.indices -> season.number to episodes[targetIndex].number
            direction > 0 -> {
                val nextSeason = seasons.filter { it.number > season.number }.minByOrNull { it.number }
                nextSeason?.episodes?.minByOrNull { it.number }?.let { nextSeason.number to it.number }
            }
            else -> {
                val prevSeason = seasons.filter { it.number < season.number }.maxByOrNull { it.number }
                prevSeason?.episodes?.maxByOrNull { it.number }?.let { prevSeason.number to it.number }
            }
        }
    }

    private fun updateEpisodeNavState() {
        _uiState.value = _uiState.value.copy(
            hasNextEpisode = adjacentEpisode(1) != null,
            hasPreviousEpisode = adjacentEpisode(-1) != null,
        )
    }

    private fun episodeTitleFor(seasonNumber: Int, episodeNumber: Int): String? =
        seasons.find { it.number == seasonNumber }
            ?.episodes?.find { it.number == episodeNumber }
            ?.title

    private fun startTicker() {
        tickerJob = viewModelScope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    _uiState.value = _uiState.value.copy(
                        positionMs = player.currentPosition,
                        durationMs = player.duration.coerceAtLeast(0),
                        bufferedPercentage = player.bufferedPercentage,
                    )
                    saveProgress(force = false)
                }
                delay(500)
            }
        }
    }

    private var lastSaveAtMs = 0L

    private fun saveProgress(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSaveAtMs < 5_000) return
        lastSaveAtMs = now
        val duration = player.duration
        if (duration <= 0) return
        val state = _uiState.value
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            historyRepository.saveProgress(
                WatchProgress(
                    animeId = animeId,
                    animeTitle = state.animeTitle,
                    posterUrl = state.posterUrl,
                    seasonNumber = state.seasonNumber,
                    episodeNumber = state.episodeNumber,
                    episodeTitle = state.episodeTitle,
                    positionMs = player.currentPosition,
                    durationMs = duration,
                    updatedAtEpochMs = now,
                ),
            )
        }
    }

    override fun onCleared() {
        saveProgress(force = true)
        tickerJob?.cancel()
        autoplayJob?.cancel()
        player.removeListener(playerListener)
        player.release()
        super.onCleared()
    }

    companion object {
        private const val AUTOPLAY_COUNTDOWN_SECONDS = 8
    }
}
