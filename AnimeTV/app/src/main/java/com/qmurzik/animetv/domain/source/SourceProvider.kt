package com.qmurzik.animetv.domain.source

import com.qmurzik.animetv.domain.model.AnimeDetails
import com.qmurzik.animetv.domain.model.AnimeSummary
import com.qmurzik.animetv.domain.model.PlaybackSource
import com.qmurzik.animetv.domain.model.Season

/**
 * Declares what one source is actually able to do, so callers (and the registries in
 * SourceRegistry.kt) can skip a provider instead of calling into it and getting
 * [SourceError.Unsupported] back. Kept as plain booleans rather than a bitmask/enum set -
 * this is checked in hot UI paths and needs to stay trivial to read.
 */
data class SourceCapabilities(
    val search: Boolean = false,
    val metadata: Boolean = false,
    val episodes: Boolean = false,
    val streaming: Boolean = false,
    val subtitles: Boolean = false,
    val multipleAudioTracks: Boolean = false,
    val adaptiveBitrate: Boolean = false,
)

/** Common identity every concrete source (Jikan, AniList, the demo streamer, a future
 *  addition) implements. See docs/adding-a-source.md / README "Adding a new source". */
interface SourceProvider {
    val id: String
    val displayName: String
    val capabilities: SourceCapabilities
}

/** Finds candidate titles for a free-text query. */
interface SearchProvider : SourceProvider {
    suspend fun search(query: String): SourceResult<List<AnimeSummary>>
}

/** Resolves full detail-page metadata for a title already known to this source. */
interface AnimeMetadataProvider : SourceProvider {
    suspend fun getDetails(externalId: String): SourceResult<AnimeDetails>
}

/** Resolves the season/episode list for a title already known to this source. */
interface EpisodeProvider : SourceProvider {
    suspend fun getSeasons(externalId: String): SourceResult<List<Season>>
}

/** Resolves actual playback data (stream variants, audio/subtitle tracks) for one episode. */
interface StreamingProvider : SourceProvider {
    suspend fun getStreams(
        externalId: String,
        seasonNumber: Int,
        episodeNumber: Int,
    ): SourceResult<PlaybackSource>
}
