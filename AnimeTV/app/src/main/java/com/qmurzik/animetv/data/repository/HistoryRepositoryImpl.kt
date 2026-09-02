package com.qmurzik.animetv.data.repository

import com.qmurzik.animetv.data.local.db.WatchProgressDao
import com.qmurzik.animetv.data.local.db.WatchProgressEntity
import com.qmurzik.animetv.domain.repository.HistoryRepository
import com.qmurzik.animetv.domain.repository.WatchProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val dao: WatchProgressDao,
) : HistoryRepository {

    override fun observeContinueWatching(limit: Int): Flow<List<WatchProgress>> =
        dao.observeContinueWatching(limit).map { list -> list.map { it.toDomain() } }

    override fun observeHistory(limit: Int): Flow<List<WatchProgress>> =
        dao.observeHistory(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun getProgress(animeId: String, seasonNumber: Int, episodeNumber: Int): WatchProgress? =
        dao.get(animeId, seasonNumber, episodeNumber)?.toDomain()

    override suspend fun saveProgress(progress: WatchProgress) {
        // Ignore near-zero, throwaway positions so a quick preview doesn't pollute history.
        if (progress.positionMs < 3_000) return
        dao.upsert(
            WatchProgressEntity(
                animeId = progress.animeId,
                animeTitle = progress.animeTitle,
                posterUrl = progress.posterUrl,
                seasonNumber = progress.seasonNumber,
                episodeNumber = progress.episodeNumber,
                episodeTitle = progress.episodeTitle,
                positionMs = progress.positionMs,
                durationMs = progress.durationMs,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun clearHistory() = dao.clear()

    override suspend fun removeEntry(animeId: String, seasonNumber: Int, episodeNumber: Int) =
        dao.remove(animeId, seasonNumber, episodeNumber)
}

private fun WatchProgressEntity.toDomain() = WatchProgress(
    animeId = animeId,
    animeTitle = animeTitle,
    posterUrl = posterUrl,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    episodeTitle = episodeTitle,
    positionMs = positionMs,
    durationMs = durationMs,
    updatedAtEpochMs = updatedAtEpochMs,
)
