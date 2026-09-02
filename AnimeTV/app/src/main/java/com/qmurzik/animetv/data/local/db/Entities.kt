package com.qmurzik.animetv.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.qmurzik.animetv.domain.model.AnimeFormat
import com.qmurzik.animetv.domain.model.SourceRef

/**
 * Local cache of every merged [com.qmurzik.animetv.domain.model.AnimeSummary] the app has
 * seen (search results, home rows). This is what lets [AnimeId] -> [SourceRef]s resolution
 * work later for details/episodes/streams without re-running the search aggregation, and is
 * capped/pruned by [AnimeIndexDao] so it can't grow without bound (item 12: cache limits).
 */
@Entity(tableName = "anime_index")
data class AnimeIndexEntity(
    @PrimaryKey val animeId: String,
    val title: String,
    val titleEnglish: String?,
    val titleNative: String?,
    val posterUrl: String?,
    val year: Int?,
    val rating: Double?,
    val format: AnimeFormat,
    val sourceRefs: List<SourceRef>,
    val cachedAtEpochMs: Long,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val animeId: String,
    val title: String,
    val titleEnglish: String?,
    val posterUrl: String?,
    val year: Int?,
    val rating: Double?,
    val format: AnimeFormat,
    val sourceRefs: List<SourceRef>,
    val addedAtEpochMs: Long,
)

@Entity(tableName = "watch_progress", primaryKeys = ["animeId", "seasonNumber", "episodeNumber"])
data class WatchProgressEntity(
    val animeId: String,
    val animeTitle: String,
    val posterUrl: String?,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeTitle: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAtEpochMs: Long,
)
