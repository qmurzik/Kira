package com.qmurzik.animetv.data.remote.animego

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * AnimeGo (https://animego.org) - Russian anime aggregator with streaming.
 * Provides a public API for accessing anime information and streams.
 * No authentication required.
 */
interface AnimeGoApi {

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20,
    ): AnimeGoSearchResponse

    @GET("anime/info")
    suspend fun getAnimeInfo(
        @Query("id") id: String,
    ): AnimeGoAnimeResponse

    @GET("anime/episodes")
    suspend fun getEpisodes(
        @Query("id") id: String,
    ): AnimeGoEpisodesResponse

    @GET("episode/stream")
    suspend fun getStreamUrl(
        @Query("episode_id") episodeId: String,
    ): AnimeGoStreamResponse
}

@Serializable
data class AnimeGoSearchResponse(
    val success: Boolean = false,
    val data: List<AnimeGoSearchResult> = emptyList(),
    val error: String? = null,
)

@Serializable
data class AnimeGoSearchResult(
    val id: String,
    val title: String,
    @SerialName("title_ru")
    val titleRu: String? = null,
    val image: String? = null,
    val year: Int? = null,
)

@Serializable
data class AnimeGoAnimeResponse(
    val success: Boolean = false,
    val data: AnimeGoAnimeInfo? = null,
    val error: String? = null,
)

@Serializable
data class AnimeGoAnimeInfo(
    val id: String,
    val title: String,
    @SerialName("title_ru")
    val titleRu: String? = null,
    val description: String? = null,
    val image: String? = null,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    @SerialName("episodes_count")
    val episodesCount: Int? = null,
)

@Serializable
data class AnimeGoEpisodesResponse(
    val success: Boolean = false,
    val data: List<AnimeGoEpisode> = emptyList(),
    val error: String? = null,
)

@Serializable
data class AnimeGoEpisode(
    val id: String,
    @SerialName("episode_number")
    val episodeNumber: Int,
    val title: String? = null,
)

@Serializable
data class AnimeGoStreamResponse(
    val success: Boolean = false,
    val data: AnimeGoStreamData? = null,
    val error: String? = null,
)

@Serializable
data class AnimeGoStreamData(
    @SerialName("stream_url")
    val streamUrl: String,
    @SerialName("quality")
    val quality: String? = null,
    @SerialName("qualities")
    val qualities: List<AnimeGoQuality> = emptyList(),
)

@Serializable
data class AnimeGoQuality(
    val label: String,
    val url: String,
)
