package com.qmurzik.animetv.data.remote.jikan

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Jikan (https://jikan.moe) - a free, key-less, public REST wrapper around the MyAnimeList
 * catalog. No authentication, no ToS violation: it's explicitly built and rate-limited
 * (~3 req/s, 60/min) for third-party consumption. Used here as the primary metadata/search
 * source. See README "Sources" for details and links.
 */
interface JikanApi {

    @GET("anime")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = 15,
        @Query("sfw") safeForWork: Boolean = true,
    ): JikanListResponse<JikanAnime>

    @GET("anime/{id}/full")
    suspend fun getAnimeFull(@Path("id") id: Int): JikanDataResponse<JikanAnimeFull>

    @GET("anime/{id}/episodes")
    suspend fun getEpisodes(@Path("id") id: Int, @Query("page") page: Int = 1): JikanListResponse<JikanEpisode>

    @GET("top/anime")
    suspend fun getTopAnime(
        @Query("filter") filter: String? = null,
        @Query("limit") limit: Int = 15,
    ): JikanListResponse<JikanAnime>

    @GET("seasons/now")
    suspend fun getCurrentSeason(@Query("limit") limit: Int = 15): JikanListResponse<JikanAnime>
}

@Serializable
data class JikanListResponse<T>(
    val data: List<T> = emptyList(),
    val pagination: JikanPagination? = null,
)

@Serializable
data class JikanDataResponse<T>(val data: T)

@Serializable
data class JikanPagination(
    @SerialName("has_next_page") val hasNextPage: Boolean = false,
)

@Serializable
data class JikanAnime(
    @SerialName("mal_id") val malId: Int,
    val url: String? = null,
    val images: JikanImages? = null,
    val title: String? = null,
    @SerialName("title_english") val titleEnglish: String? = null,
    @SerialName("title_japanese") val titleJapanese: String? = null,
    val type: String? = null,
    val episodes: Int? = null,
    val status: String? = null,
    val score: Double? = null,
    val year: Int? = null,
    val genres: List<JikanNamed> = emptyList(),
)

@Serializable
data class JikanAnimeFull(
    @SerialName("mal_id") val malId: Int,
    val images: JikanImages? = null,
    val trailer: JikanTrailer? = null,
    val title: String? = null,
    @SerialName("title_english") val titleEnglish: String? = null,
    @SerialName("title_japanese") val titleJapanese: String? = null,
    val titles: List<JikanTitleEntry> = emptyList(),
    val synopsis: String? = null,
    val type: String? = null,
    val episodes: Int? = null,
    val status: String? = null,
    val score: Double? = null,
    val year: Int? = null,
    val genres: List<JikanNamed> = emptyList(),
    val themes: List<JikanNamed> = emptyList(),
)

@Serializable
data class JikanTitleEntry(val type: String? = null, val title: String? = null)

@Serializable
data class JikanTrailer(@SerialName("images") val images: JikanImages? = null)

@Serializable
data class JikanImages(val jpg: JikanImageSet? = null, val webp: JikanImageSet? = null)

@Serializable
data class JikanImageSet(
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("large_image_url") val largeImageUrl: String? = null,
)

@Serializable
data class JikanNamed(@SerialName("mal_id") val malId: Int? = null, val name: String)

@Serializable
data class JikanEpisode(
    @SerialName("mal_id") val malId: Int,
    val title: String? = null,
    val duration: Int? = null,
)
