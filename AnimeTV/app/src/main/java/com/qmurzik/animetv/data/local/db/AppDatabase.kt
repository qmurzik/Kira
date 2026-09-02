package com.qmurzik.animetv.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        AnimeIndexEntity::class,
        FavoriteEntity::class,
        WatchProgressEntity::class,
        SearchHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun animeIndexDao(): AnimeIndexDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        const val NAME = "animetv.db"
    }
}
