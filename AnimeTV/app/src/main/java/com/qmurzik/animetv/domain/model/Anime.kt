package com.qmurzik.animetv.domain.model

import kotlinx.serialization.Serializable

/**
 * A pointer to the same title inside one specific source's catalog.
 * A merged [AnimeSummary]/[AnimeDetails] can carry several of these - one
 * per provider that recognises the title - which is what lets the app fall
 * back from one metadata/streaming provider to the next for the same anime.
 *
 * [Serializable] so [com.qmurzik.animetv.data.local.db.Converters] can persist the list of
 * refs on [com.qmurzik.animetv.data.local.db.AnimeIndexEntity] / `FavoriteEntity` as JSON -
 * the only reason this otherwise-platform-agnostic domain model carries the annotation.
 */
@Serializable
data class SourceRef(
    val sourceId: String,
    val externalId: String,
)

enum class AnimeStatus {
    AIRING,
    FINISHED,
    UPCOMING,
    UNKNOWN,
}

enum class AnimeFormat {
    TV,
    MOVIE,
    OVA,
    ONA,
    SPECIAL,
    MUSIC,
    UNKNOWN,
}

/**
 * Stable, provider-independent identity for a title inside this app.
 * Built by normalising the primary title (see TitleNormalizer) so that the
 * same anime discovered through different sources collapses to one card.
 */
typealias AnimeId = String

/** Lightweight representation used in horizontal rows / search results. */
data class AnimeSummary(
    val id: AnimeId,
    val title: String,
    val titleEnglish: String? = null,
    val titleNative: String? = null,
    val posterUrl: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val format: AnimeFormat = AnimeFormat.UNKNOWN,
    val sourceRefs: List<SourceRef> = emptyList(),
)

/** Full detail-page representation, merged from every source that matched. */
data class AnimeDetails(
    val id: AnimeId,
    val title: String,
    val titleEnglish: String? = null,
    val titleNative: String? = null,
    val titleSynonyms: List<String> = emptyList(),
    val synopsis: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val genres: List<String> = emptyList(),
    val year: Int? = null,
    val rating: Double? = null,
    val status: AnimeStatus = AnimeStatus.UNKNOWN,
    val format: AnimeFormat = AnimeFormat.UNKNOWN,
    val episodeCount: Int? = null,
    val seasons: List<Season> = emptyList(),
    val sourceRefs: List<SourceRef> = emptyList(),
    /** Legal, licensed places this title can be watched officially (AniList `externalLinks`
     *  of type STREAMING - e.g. Crunchyroll, Netflix, HIDIVE). These open the official site
     *  in the browser; the app never re-streams or embeds their video. See README "Sources". */
    val officialLinks: List<OfficialWatchLink> = emptyList(),
) {
    fun toSummary() = AnimeSummary(
        id = id,
        title = title,
        titleEnglish = titleEnglish,
        titleNative = titleNative,
        posterUrl = posterUrl,
        year = year,
        rating = rating,
        format = format,
        sourceRefs = sourceRefs,
    )
}

data class OfficialWatchLink(
    val siteName: String,
    val url: String,
)

data class Season(
    val number: Int,
    val name: String? = null,
    val episodes: List<Episode> = emptyList(),
)

data class Episode(
    val number: Int,
    val seasonNumber: Int,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Int? = null,
    val synopsis: String? = null,
)

data class AudioTrack(
    val id: String,
    val language: String,
    val label: String,
)

data class SubtitleTrack(
    val id: String,
    val language: String,
    val label: String,
    /** Null when the track is muxed into the stream itself (e.g. HLS/DASH text renditions). */
    val url: String? = null,
    val mimeType: String = "text/vtt",
)

data class StreamVariant(
    val url: String,
    val qualityLabel: String,
    val isAdaptive: Boolean,
    val headers: Map<String, String> = emptyMap(),
)

/** Approximate opening/ending skip window, in milliseconds, when a source publishes one. */
data class SkipSegment(
    val startMs: Long,
    val endMs: Long,
)

/**
 * Everything a [com.qmurzik.animetv.domain.source.StreamingProvider] returns for one episode.
 * [isDemoContent] is true only for the bundled sample/demo provider - see StreamingProvider.kt.
 */
data class PlaybackSource(
    val providerId: String,
    val providerLabel: String,
    val variants: List<StreamVariant>,
    val audioTracks: List<AudioTrack> = emptyList(),
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val openingSkip: SkipSegment? = null,
    val endingSkip: SkipSegment? = null,
    val isDemoContent: Boolean = false,
)
