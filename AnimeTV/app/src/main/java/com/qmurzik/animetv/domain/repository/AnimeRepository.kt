package com.qmurzik.animetv.domain.repository

import com.qmurzik.animetv.domain.model.AnimeDetails
import com.qmurzik.animetv.domain.model.AnimeSummary
import com.qmurzik.animetv.domain.model.PlaybackSource
import com.qmurzik.animetv.domain.model.Season
import com.qmurzik.animetv.domain.source.SourceError

/** UI-facing outcome: unlike [com.qmurzik.animetv.domain.source.SourceResult] this also
 *  carries the list of sources that were tried and failed, for a precise "retry / try another
 *  source" error screen. */
sealed class AnimeOutcome<out T> {
    data class Success<T>(val value: T, val partial: Boolean = false) : AnimeOutcome<T>()
    data class Error(val reason: SourceError) : AnimeOutcome<Nothing>()
}

interface AnimeRepository {
    suspend fun search(query: String): AnimeOutcome<List<AnimeSummary>>
    suspend fun getDetails(animeId: String): AnimeOutcome<AnimeDetails>
    suspend fun getSeasons(animeId: String): AnimeOutcome<List<Season>>
    suspend fun getStreams(animeId: String, seasonNumber: Int, episodeNumber: Int): AnimeOutcome<PlaybackSource>

    suspend fun homeSections(): AnimeOutcome<HomeSections>
}

data class HomeSections(
    val trending: List<AnimeSummary>,
    val popular: List<AnimeSummary>,
    val recentlyAdded: List<AnimeSummary>,
    val recommended: List<AnimeSummary>,
)
