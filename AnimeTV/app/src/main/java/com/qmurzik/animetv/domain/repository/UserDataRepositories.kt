package com.qmurzik.animetv.domain.repository

import com.qmurzik.animetv.domain.model.AnimeSummary
import kotlinx.coroutines.flow.Flow

enum class FavoritesSortOrder { RECENTLY_ADDED, TITLE_AZ, YEAR }

data class FavoriteItem(
    val anime: AnimeSummary,
    val addedAtEpochMs: Long,
)

interface FavoritesRepository {
    fun observeFavorites(sortOrder: FavoritesSortOrder): Flow<List<FavoriteItem>>
    fun isFavorite(animeId: String): Flow<Boolean>
    suspend fun add(anime: AnimeSummary)
    suspend fun remove(animeId: String)
    suspend fun toggle(anime: AnimeSummary)
}

data class WatchProgress(
    val animeId: String,
    val animeTitle: String,
    val posterUrl: String?,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeTitle: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtEpochMs: Long,
) {
    val fractionWatched: Float
        get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    val isFinished: Boolean
        get() = durationMs > 0 && positionMs >= durationMs * 0.95
}

interface HistoryRepository {
    fun observeContinueWatching(limit: Int = 20): Flow<List<WatchProgress>>
    fun observeHistory(limit: Int = 200): Flow<List<WatchProgress>>
    suspend fun getProgress(animeId: String, seasonNumber: Int, episodeNumber: Int): WatchProgress?
    suspend fun saveProgress(progress: WatchProgress)
    suspend fun clearHistory()
    suspend fun removeEntry(animeId: String, seasonNumber: Int, episodeNumber: Int)
}

interface SearchHistoryRepository {
    fun observeRecentQueries(limit: Int = 10): Flow<List<String>>
    suspend fun record(query: String)
    suspend fun clear()
}
