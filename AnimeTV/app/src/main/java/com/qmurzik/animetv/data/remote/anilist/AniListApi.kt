package com.qmurzik.animetv.data.remote.anilist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * AniList (https://anilist.co/graphql) - a free, key-less, public GraphQL API used here as
 * the fallback metadata/search source, and as the source of [AniListMedia.externalLinks],
 * which point at the *official* licensed streaming sites for a title (Crunchyroll, Netflix,
 * HIDIVE, ...). No authentication is required for these public, non-user-scoped queries.
 */
interface AniListApi {
    @POST(".")
    suspend fun execute(@Body request: GraphQlRequest): AniListEnvelope
}

@Serializable
data class GraphQlRequest(val query: String, val variables: JsonElement)

@Serializable
data class AniListEnvelope(val data: AniListData? = null, val errors: List<AniListGraphQlError>? = null)

@Serializable
data class AniListGraphQlError(val message: String? = null)

@Serializable
data class AniListData(
    @SerialName("Media") val media: AniListMedia? = null,
    @SerialName("Page") val page: AniListPage? = null,
)

@Serializable
data class AniListPage(val media: List<AniListMedia> = emptyList())

@Serializable
data class AniListMedia(
    val id: Int,
    val title: AniListTitle? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val episodes: Int? = null,
    val seasonYear: Int? = null,
    val averageScore: Int? = null,
    val status: String? = null,
    val format: String? = null,
    val coverImage: AniListCoverImage? = null,
    val bannerImage: String? = null,
    val externalLinks: List<AniListExternalLink> = emptyList(),
)

@Serializable
data class AniListTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
)

@Serializable
data class AniListCoverImage(
    val extraLarge: String? = null,
    val large: String? = null,
)

@Serializable
data class AniListExternalLink(
    val site: String? = null,
    val url: String? = null,
    val type: String? = null,
)

/** GraphQL query bodies, kept next to the API since AniList has no separate query DSL here. */
object AniListQueries {

    const val SEARCH = """
        query (${'$'}search: String) {
          Page(page: 1, perPage: 15) {
            media(search: ${'$'}search, type: ANIME, sort: SEARCH_MATCH) {
              id
              title { romaji english native }
              coverImage { extraLarge large }
              episodes
              seasonYear
              averageScore
              format
              status
            }
          }
        }
    """

    const val DETAILS = """
        query (${'$'}id: Int) {
          Media(id: ${'$'}id, type: ANIME) {
            id
            title { romaji english native }
            description(asHtml: false)
            genres
            episodes
            seasonYear
            averageScore
            status
            format
            coverImage { extraLarge large }
            bannerImage
            externalLinks { site url type }
          }
        }
    """

    const val TRENDING = """
        query {
          Page(page: 1, perPage: 15) {
            media(type: ANIME, sort: TRENDING_DESC) {
              id
              title { romaji english native }
              coverImage { extraLarge large }
              episodes
              seasonYear
              averageScore
              format
              status
            }
          }
        }
    """

    const val POPULAR = """
        query {
          Page(page: 1, perPage: 15) {
            media(type: ANIME, sort: POPULARITY_DESC) {
              id
              title { romaji english native }
              coverImage { extraLarge large }
              episodes
              seasonYear
              averageScore
              format
              status
            }
          }
        }
    """
}
