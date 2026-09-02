package com.qmurzik.animetv.data.remote.shikimori

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Shikimori (https://shikimori.one) - a free, key-less, public REST API backing a large
 * Russian-language anime tracking/database community site (the closest local equivalent of
 * MyAnimeList). No authentication is required for these public catalog endpoints; Shikimori's
 * own API docs (https://shikimori.one/api/doc) only ask that clients send a distinct
 * `User-Agent` for rate-limiting purposes, which [com.qmurzik.animetv.di.NetworkModule] does.
 *
 * Used here purely as a metadata/search source with Russian-language titles and synopses -
 * exactly like [com.qmurzik.animetv.data.remote.jikan.JikanApi] and
 * [com.qmurzik.animetv.data.remote.anilist.AniListApi], it returns catalog data, never video.
 * Deliberately NOT used: its `/animes/{id}/videos` endpoint, which surfaces community-submitted
 * links to third-party fansub/fandub video hosts of unclear licensing - wiring that up would
 * cross the "no pirate sites" line from this project's brief, so it's skipped entirely. See
 * README "Sources & limitations" for the full reasoning.
 */
interface ShikimoriApi {

    @GET("animes")
    suspend fun search(
        @Query("search") query: String,
        @Query("limit") limit: Int = 15,
        @Query("order") order: String = "popularity",
    ): List<ShikimoriAnime>

    @GET("animes/{id}")
    suspend fun getAnime(@Path("id") id: Int): ShikimoriAnime
}

@Serializable
data class ShikimoriAnime(
    val id: Int,
    val name: String? = null,
    val russian: String? = null,
    val image: ShikimoriImage? = null,
    val kind: String? = null,
    val score: String? = null,
    val status: String? = null,
    val episodes: Int? = null,
    @SerialName("aired_on") val airedOn: String? = null,
    val genres: List<ShikimoriGenre> = emptyList(),
    val description: String? = null,
)

@Serializable
data class ShikimoriImage(
    val original: String? = null,
    val preview: String? = null,
)

@Serializable
data class ShikimoriGenre(
    val id: Int? = null,
    val name: String? = null,
    val russian: String? = null,
)
