package com.qmurzik.animetv.data.source.anilist

import com.qmurzik.animetv.data.remote.anilist.AniListApi
import com.qmurzik.animetv.data.remote.anilist.AniListMedia
import com.qmurzik.animetv.data.remote.anilist.AniListQueries
import com.qmurzik.animetv.data.remote.anilist.GraphQlRequest
import com.qmurzik.animetv.domain.model.AnimeDetails
import com.qmurzik.animetv.domain.model.AnimeFormat
import com.qmurzik.animetv.domain.model.AnimeStatus
import com.qmurzik.animetv.domain.model.AnimeSummary
import com.qmurzik.animetv.domain.model.OfficialWatchLink
import com.qmurzik.animetv.domain.model.SourceRef
import com.qmurzik.animetv.domain.source.AnimeMetadataProvider
import com.qmurzik.animetv.domain.source.SearchProvider
import com.qmurzik.animetv.domain.source.SourceCapabilities
import com.qmurzik.animetv.domain.source.SourceResult
import com.qmurzik.animetv.domain.source.sourceResultOf
import com.qmurzik.animetv.util.RateLimiter
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

private const val SOURCE_ID = "anilist"

/**
 * Metadata/search fallback backed by AniList's public GraphQL API - see AniListApi.kt.
 * Also the source of [AnimeDetails.officialLinks]: AniList's `externalLinks(type: STREAMING)`
 * point to the title's *official*, licensed streaming pages (Crunchyroll, Netflix, HIDIVE...).
 * AniList, like MAL, treats sequel seasons as separate media entries, so - same as
 * [com.qmurzik.animetv.data.source.jikan.JikanSource] - this always reports a single season.
 */
@Singleton
class AniListSource @Inject constructor(
    private val api: AniListApi,
) : SearchProvider, AnimeMetadataProvider {

    override val id: String = SOURCE_ID
    override val displayName: String = "AniList"
    override val capabilities = SourceCapabilities(search = true, metadata = true)

    // AniList's public endpoint documents a 90 req/min budget; stay well under it.
    private val rateLimiter = RateLimiter(minIntervalMs = 700)

    override suspend fun search(query: String): SourceResult<List<AnimeSummary>> = sourceResultOf(id) {
        val variables = buildJsonObject { put("search", query) }
        val envelope = rateLimiter.throttled {
            api.execute(GraphQlRequest(AniListQueries.SEARCH, variables))
        }
        val media = envelope.data?.page?.media ?: throw IllegalStateException(
            envelope.errors?.firstOrNull()?.message ?: "AniList returned no data",
        )
        media.map { it.toSummary() }
    }

    override suspend fun getDetails(externalId: String): SourceResult<AnimeDetails> = sourceResultOf(id) {
        val variables = buildJsonObject { put("id", externalId.toInt()) }
        val envelope = rateLimiter.throttled {
            api.execute(GraphQlRequest(AniListQueries.DETAILS, variables))
        }
        val media = envelope.data?.media ?: throw IllegalStateException(
            envelope.errors?.firstOrNull()?.message ?: "Anime not found on AniList",
        )
        media.toDetails()
    }

    suspend fun trending(): SourceResult<List<AnimeSummary>> = fetchCurated(AniListQueries.TRENDING)
    suspend fun popular(): SourceResult<List<AnimeSummary>> = fetchCurated(AniListQueries.POPULAR)

    private suspend fun fetchCurated(query: String): SourceResult<List<AnimeSummary>> = sourceResultOf(id) {
        val envelope = rateLimiter.throttled { api.execute(GraphQlRequest(query, JsonNull)) }
        val media = envelope.data?.page?.media
            ?: throw IllegalStateException(envelope.errors?.firstOrNull()?.message ?: "empty")
        media.map { it.toSummary() }
    }

    private fun AniListMedia.toSummary() = AnimeSummary(
        id = bestTitle(),
        title = bestTitle(),
        titleEnglish = title?.english,
        titleNative = title?.native,
        posterUrl = coverImage?.extraLarge ?: coverImage?.large,
        year = seasonYear,
        rating = averageScore?.let { it / 10.0 },
        format = format.toAnimeFormat(),
        sourceRefs = listOf(SourceRef(SOURCE_ID, id.toString())),
    )

    private fun AniListMedia.toDetails() = AnimeDetails(
        id = bestTitle(),
        title = bestTitle(),
        titleEnglish = title?.english,
        titleNative = title?.native,
        synopsis = description?.replace(Regex("<[^>]*>"), ""),
        posterUrl = coverImage?.extraLarge ?: coverImage?.large,
        backdropUrl = bannerImage ?: coverImage?.extraLarge,
        genres = genres,
        year = seasonYear,
        rating = averageScore?.let { it / 10.0 },
        status = status.toAnimeStatus(),
        format = format.toAnimeFormat(),
        episodeCount = episodes,
        sourceRefs = listOf(SourceRef(SOURCE_ID, id.toString())),
        officialLinks = externalLinks
            .filter { it.type.equals("STREAMING", ignoreCase = true) && it.url != null }
            .mapNotNull { link -> link.url?.let { OfficialWatchLink(link.site ?: "Official site", it) } },
    )

    private fun AniListMedia.bestTitle(): String =
        title?.english ?: title?.romaji ?: title?.native ?: "AniList #$id"
}

private fun String?.toAnimeStatus(): AnimeStatus = when (this) {
    "RELEASING" -> AnimeStatus.AIRING
    "FINISHED" -> AnimeStatus.FINISHED
    "NOT_YET_RELEASED" -> AnimeStatus.UPCOMING
    else -> AnimeStatus.UNKNOWN
}

private fun String?.toAnimeFormat(): AnimeFormat = when (this) {
    "TV", "TV_SHORT" -> AnimeFormat.TV
    "MOVIE" -> AnimeFormat.MOVIE
    "OVA" -> AnimeFormat.OVA
    "ONA" -> AnimeFormat.ONA
    "SPECIAL" -> AnimeFormat.SPECIAL
    "MUSIC" -> AnimeFormat.MUSIC
    else -> AnimeFormat.UNKNOWN
}
