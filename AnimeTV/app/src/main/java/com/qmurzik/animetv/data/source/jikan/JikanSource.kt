package com.qmurzik.animetv.data.source.jikan

import com.qmurzik.animetv.data.remote.jikan.JikanAnime
import com.qmurzik.animetv.data.remote.jikan.JikanAnimeFull
import com.qmurzik.animetv.data.remote.jikan.JikanApi
import com.qmurzik.animetv.domain.model.AnimeDetails
import com.qmurzik.animetv.domain.model.AnimeFormat
import com.qmurzik.animetv.domain.model.AnimeStatus
import com.qmurzik.animetv.domain.model.AnimeSummary
import com.qmurzik.animetv.domain.model.Episode
import com.qmurzik.animetv.domain.model.Season
import com.qmurzik.animetv.domain.model.SourceRef
import com.qmurzik.animetv.domain.source.AnimeMetadataProvider
import com.qmurzik.animetv.domain.source.EpisodeProvider
import com.qmurzik.animetv.domain.source.SearchProvider
import com.qmurzik.animetv.domain.source.SourceCapabilities
import com.qmurzik.animetv.domain.source.SourceResult
import com.qmurzik.animetv.domain.source.sourceResultOf
import com.qmurzik.animetv.util.RateLimiter
import javax.inject.Inject
import javax.inject.Singleton

private const val SOURCE_ID = "jikan"

/**
 * Metadata/search/episode source backed by Jikan (MyAnimeList). See JikanApi.kt for the
 * legal basis. MAL - and therefore Jikan - models each "season" of a show (e.g. a sequel
 * cour) as its own separate catalog entry rather than a season nested under one entry, so
 * this source always reports a single [Season] (number = 1) holding that entry's episodes;
 * a sequel season shows up as its own search result/anime, exactly as it does on MAL itself.
 */
@Singleton
class JikanSource @Inject constructor(
    private val api: JikanApi,
) : SearchProvider, AnimeMetadataProvider, EpisodeProvider {

    override val id: String = SOURCE_ID
    override val displayName: String = "MyAnimeList (Jikan)"
    override val capabilities = SourceCapabilities(search = true, metadata = true, episodes = true)

    // Jikan's public deployment asks third-party clients to stay near ~3 req/s.
    private val rateLimiter = RateLimiter(minIntervalMs = 350)

    override suspend fun search(query: String): SourceResult<List<AnimeSummary>> = sourceResultOf(id) {
        val response = rateLimiter.throttled { api.search(query = query) }
        response.data.map { it.toSummary() }
    }

    override suspend fun getDetails(externalId: String): SourceResult<AnimeDetails> = sourceResultOf(id) {
        val malId = externalId.toInt()
        val response = rateLimiter.throttled { api.getAnimeFull(malId) }
        response.data.toDetails()
    }

    override suspend fun getSeasons(externalId: String): SourceResult<List<Season>> = sourceResultOf(id) {
        val malId = externalId.toInt()
        val episodes = rateLimiter.throttled { api.getEpisodes(malId) }
        val mapped = episodes.data.mapIndexed { index, ep ->
            Episode(
                number = index + 1,
                seasonNumber = 1,
                title = ep.title,
                durationSeconds = ep.duration,
            )
        }
        listOf(Season(number = 1, episodes = mapped))
    }

    private fun JikanAnime.toSummary() = AnimeSummary(
        id = title ?: "mal_$malId",
        title = title ?: titleEnglish ?: "MAL #$malId",
        titleEnglish = titleEnglish,
        titleNative = titleJapanese,
        posterUrl = images?.webp?.largeImageUrl ?: images?.jpg?.largeImageUrl,
        year = year,
        rating = score,
        format = type.toFormat(),
        sourceRefs = listOf(SourceRef(SOURCE_ID, malId.toString())),
    )

    private fun JikanAnimeFull.toDetails() = AnimeDetails(
        id = title ?: "mal_$malId",
        title = title ?: titleEnglish ?: "MAL #$malId",
        titleEnglish = titleEnglish,
        titleNative = titleJapanese,
        titleSynonyms = titles.mapNotNull { it.title }.filter { it != title },
        synopsis = synopsis,
        posterUrl = images?.webp?.largeImageUrl ?: images?.jpg?.largeImageUrl,
        backdropUrl = images?.webp?.largeImageUrl ?: images?.jpg?.largeImageUrl,
        genres = (genres + themes).map { it.name },
        year = year,
        rating = score,
        status = status.toStatus(),
        format = type.toFormat(),
        episodeCount = episodes,
        sourceRefs = listOf(SourceRef(SOURCE_ID, malId.toString())),
    )
}

private fun String?.toStatus(): AnimeStatus = when {
    this == null -> AnimeStatus.UNKNOWN
    contains("Airing", ignoreCase = true) -> AnimeStatus.AIRING
    contains("Finished", ignoreCase = true) -> AnimeStatus.FINISHED
    contains("Not yet aired", ignoreCase = true) -> AnimeStatus.UPCOMING
    else -> AnimeStatus.UNKNOWN
}

private fun String?.toFormat(): AnimeFormat = when (this?.uppercase()) {
    "TV" -> AnimeFormat.TV
    "MOVIE" -> AnimeFormat.MOVIE
    "OVA" -> AnimeFormat.OVA
    "ONA" -> AnimeFormat.ONA
    "SPECIAL" -> AnimeFormat.SPECIAL
    "MUSIC" -> AnimeFormat.MUSIC
    else -> AnimeFormat.UNKNOWN
}
