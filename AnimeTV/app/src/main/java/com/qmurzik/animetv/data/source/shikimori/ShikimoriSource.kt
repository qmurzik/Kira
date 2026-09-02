package com.qmurzik.animetv.data.source.shikimori

import com.qmurzik.animetv.data.remote.shikimori.ShikimoriAnime
import com.qmurzik.animetv.data.remote.shikimori.ShikimoriApi
import com.qmurzik.animetv.domain.model.AnimeDetails
import com.qmurzik.animetv.domain.model.AnimeFormat
import com.qmurzik.animetv.domain.model.AnimeStatus
import com.qmurzik.animetv.domain.model.AnimeSummary
import com.qmurzik.animetv.domain.model.SourceRef
import com.qmurzik.animetv.domain.source.AnimeMetadataProvider
import com.qmurzik.animetv.domain.source.SearchProvider
import com.qmurzik.animetv.domain.source.SourceCapabilities
import com.qmurzik.animetv.domain.source.SourceResult
import com.qmurzik.animetv.domain.source.sourceResultOf
import com.qmurzik.animetv.util.RateLimiter
import javax.inject.Inject
import javax.inject.Singleton

const val SHIKIMORI_SOURCE_ID = "shikimori"

private const val SHIKIMORI_ORIGIN = "https://shikimori.one"

/**
 * Metadata/search source backed by Shikimori (see ShikimoriApi.kt for the legal basis).
 * Its main value over Jikan/AniList is that titles and synopses come back in Russian
 * (`russian`/`description`), which [com.qmurzik.animetv.data.repository.AnimeRepositoryImpl]
 * prioritises automatically when the app's language is set to Russian. Like the other two
 * metadata sources it does not provide video - only catalog data.
 */
@Singleton
class ShikimoriSource @Inject constructor(
    private val api: ShikimoriApi,
) : SearchProvider, AnimeMetadataProvider {

    override val id: String = SHIKIMORI_SOURCE_ID
    override val displayName: String = "Shikimori"
    override val capabilities = SourceCapabilities(search = true, metadata = true)

    // Shikimori's docs ask third-party clients to stay comfortably under ~5 req/s.
    private val rateLimiter = RateLimiter(minIntervalMs = 300)

    override suspend fun search(query: String): SourceResult<List<AnimeSummary>> = sourceResultOf(id) {
        rateLimiter.throttled { api.search(query = query) }.map { it.toSummary() }
    }

    override suspend fun getDetails(externalId: String): SourceResult<AnimeDetails> = sourceResultOf(id) {
        rateLimiter.throttled { api.getAnime(externalId.toInt()) }.toDetails()
    }

    private fun ShikimoriAnime.toSummary() = AnimeSummary(
        id = bestTitle(),
        title = bestTitle(),
        titleEnglish = name,
        posterUrl = image?.original?.toAbsoluteUrl(),
        year = airedOn?.take(4)?.toIntOrNull(),
        rating = score?.toDoubleOrNull(),
        format = kind.toAnimeFormat(),
        sourceRefs = listOf(SourceRef(SHIKIMORI_SOURCE_ID, id.toString())),
    )

    private fun ShikimoriAnime.toDetails() = AnimeDetails(
        id = bestTitle(),
        title = bestTitle(),
        titleEnglish = name,
        synopsis = description?.stripBbCodeAndHtml(),
        posterUrl = image?.original?.toAbsoluteUrl(),
        backdropUrl = image?.original?.toAbsoluteUrl(),
        genres = genres.map { it.russian ?: it.name ?: "" }.filter { it.isNotBlank() },
        year = airedOn?.take(4)?.toIntOrNull(),
        rating = score?.toDoubleOrNull(),
        status = status.toAnimeStatus(),
        format = kind.toAnimeFormat(),
        episodeCount = episodes,
        sourceRefs = listOf(SourceRef(SHIKIMORI_SOURCE_ID, id.toString())),
    )

    /** Prefers the Russian title - it's this source's whole reason for being in the app. */
    private fun ShikimoriAnime.bestTitle(): String = russian?.takeIf { it.isNotBlank() } ?: name ?: "Shikimori #$id"

    private fun String.toAbsoluteUrl(): String = if (startsWith("http")) this else "$SHIKIMORI_ORIGIN$this"
}

private fun String?.stripBbCodeAndHtml(): String = this
    ?.replace(Regex("\\[[^]]*]"), "")
    ?.replace(Regex("<[^>]*>"), "")
    ?.trim()
    .orEmpty()

private fun String?.toAnimeStatus(): AnimeStatus = when (this) {
    "ongoing" -> AnimeStatus.AIRING
    "released" -> AnimeStatus.FINISHED
    "anons" -> AnimeStatus.UPCOMING
    else -> AnimeStatus.UNKNOWN
}

private fun String?.toAnimeFormat(): AnimeFormat = when (this) {
    "tv", "tv_special" -> AnimeFormat.TV
    "movie" -> AnimeFormat.MOVIE
    "ova" -> AnimeFormat.OVA
    "ona" -> AnimeFormat.ONA
    "special" -> AnimeFormat.SPECIAL
    "music" -> AnimeFormat.MUSIC
    else -> AnimeFormat.UNKNOWN
}
