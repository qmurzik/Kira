package com.qmurzik.animetv.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimeIndexDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<AnimeIndexEntity>)

    @Query("SELECT * FROM anime_index WHERE animeId = :animeId LIMIT 1")
    suspend fun get(animeId: String): AnimeIndexEntity?

    // Cache limit: keep only the most recently cached rows (item 12 - bounded local cache).
    @Query(
        "DELETE FROM anime_index WHERE animeId NOT IN " +
            "(SELECT animeId FROM anime_index ORDER BY cachedAtEpochMs DESC LIMIT :keep)",
    )
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM anime_index")
    suspend fun clearAll()
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE animeId = :animeId")
    suspend fun delete(animeId: String)

    @Query("SELECT * FROM favorites ORDER BY addedAtEpochMs DESC")
    fun observeAllByRecent(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY title ASC")
    fun observeAllByTitle(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY year DESC")
    fun observeAllByYear(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE animeId = :animeId)")
    fun observeIsFavorite(animeId: String): Flow<Boolean>
}

@Dao
interface WatchProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchProgressEntity)

    @Query(
        "SELECT * FROM watch_progress WHERE animeId = :animeId AND seasonNumber = :season " +
            "AND episodeNumber = :episode LIMIT 1",
    )
    suspend fun get(animeId: String, season: Int, episode: Int): WatchProgressEntity?

    @Query(
        "SELECT * FROM watch_progress WHERE positionMs > 0 AND durationMs > 0 " +
            "AND positionMs < durationMs * 0.95 ORDER BY updatedAtEpochMs DESC LIMIT :limit",
    )
    fun observeContinueWatching(limit: Int): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress ORDER BY updatedAtEpochMs DESC LIMIT :limit")
    fun observeHistory(limit: Int): Flow<List<WatchProgressEntity>>

    @Query("DELETE FROM watch_progress")
    suspend fun clear()

    @Query(
        "DELETE FROM watch_progress WHERE animeId = :animeId AND seasonNumber = :season " +
            "AND episodeNumber = :episode",
    )
    suspend fun remove(animeId: String, season: Int, episode: Int)
}

@Dao
interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SearchHistoryEntity)

    @Query("SELECT query FROM search_history ORDER BY searchedAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<String>>

    @Query("DELETE FROM search_history")
    suspend fun clear()

    // Cache limit for search history too.
    @Query(
        "DELETE FROM search_history WHERE query NOT IN " +
            "(SELECT query FROM search_history ORDER BY searchedAtEpochMs DESC LIMIT :keep)",
    )
    suspend fun trimTo(keep: Int)
}
