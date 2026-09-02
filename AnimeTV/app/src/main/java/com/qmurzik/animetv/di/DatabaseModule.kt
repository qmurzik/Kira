package com.qmurzik.animetv.di

import android.content.Context
import androidx.room.Room
import com.qmurzik.animetv.data.local.db.AnimeIndexDao
import com.qmurzik.animetv.data.local.db.AppDatabase
import com.qmurzik.animetv.data.local.db.FavoriteDao
import com.qmurzik.animetv.data.local.db.SearchHistoryDao
import com.qmurzik.animetv.data.local.db.WatchProgressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideAnimeIndexDao(db: AppDatabase): AnimeIndexDao = db.animeIndexDao()

    @Provides
    fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideWatchProgressDao(db: AppDatabase): WatchProgressDao = db.watchProgressDao()

    @Provides
    fun provideSearchHistoryDao(db: AppDatabase): SearchHistoryDao = db.searchHistoryDao()
}
