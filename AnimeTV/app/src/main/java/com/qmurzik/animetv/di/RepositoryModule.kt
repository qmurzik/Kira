package com.qmurzik.animetv.di

import com.qmurzik.animetv.data.local.datastore.SettingsRepositoryImpl
import com.qmurzik.animetv.data.repository.AnimeRepositoryImpl
import com.qmurzik.animetv.data.repository.FavoritesRepositoryImpl
import com.qmurzik.animetv.data.repository.HistoryRepositoryImpl
import com.qmurzik.animetv.data.repository.SearchHistoryRepositoryImpl
import com.qmurzik.animetv.domain.repository.AnimeRepository
import com.qmurzik.animetv.domain.repository.FavoritesRepository
import com.qmurzik.animetv.domain.repository.HistoryRepository
import com.qmurzik.animetv.domain.repository.SearchHistoryRepository
import com.qmurzik.animetv.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAnimeRepository(impl: AnimeRepositoryImpl): AnimeRepository

    @Binds
    @Singleton
    abstract fun bindFavoritesRepository(impl: FavoritesRepositoryImpl): FavoritesRepository

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(impl: HistoryRepositoryImpl): HistoryRepository

    @Binds
    @Singleton
    abstract fun bindSearchHistoryRepository(impl: SearchHistoryRepositoryImpl): SearchHistoryRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
