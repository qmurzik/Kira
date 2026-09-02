package com.qmurzik.animetv.di

import com.qmurzik.animetv.data.source.anilist.AniListSource
import com.qmurzik.animetv.data.source.demo.DemoStreamingSource
import com.qmurzik.animetv.data.source.jikan.JikanSource
import com.qmurzik.animetv.data.source.shikimori.ShikimoriSource
import com.qmurzik.animetv.domain.source.AnimeMetadataProvider
import com.qmurzik.animetv.domain.source.EpisodeProvider
import com.qmurzik.animetv.domain.source.EpisodeRegistry
import com.qmurzik.animetv.domain.source.MetadataRegistry
import com.qmurzik.animetv.domain.source.SearchAggregator
import com.qmurzik.animetv.domain.source.SearchProvider
import com.qmurzik.animetv.domain.source.StreamingProvider
import com.qmurzik.animetv.domain.source.StreamingRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires the concrete sources into the app in fallback priority order: Jikan (MyAnimeList)
 * first as the richest free metadata source, Shikimori second (Russian-language titles and
 * synopses - see ShikimoriSource.kt; [com.qmurzik.animetv.data.repository.AnimeRepositoryImpl]
 * moves it to the front automatically when the app's language is Russian), AniList third as
 * the final metadata fallback + the source of official watch links, and the bundled demo
 * streamer last so playback always has *something* to fall back to. Adding a real licensed
 * streaming partner is a matter of implementing [StreamingProvider] and listing it here ahead
 * of [DemoStreamingSource] - see README "Adding a new source".
 */
@Module
@InstallIn(SingletonComponent::class)
object SourceModule {

    @Provides
    @Singleton
    fun provideSearchProviders(
        jikan: JikanSource,
        shikimori: ShikimoriSource,
        aniList: AniListSource,
    ): List<SearchProvider> = listOf(jikan, shikimori, aniList)

    @Provides
    @Singleton
    fun provideMetadataProviders(
        jikan: JikanSource,
        shikimori: ShikimoriSource,
        aniList: AniListSource,
    ): List<AnimeMetadataProvider> = listOf(jikan, shikimori, aniList)

    @Provides
    @Singleton
    fun provideEpisodeProviders(jikan: JikanSource): List<EpisodeProvider> = listOf(jikan)

    @Provides
    @Singleton
    fun provideStreamingProviders(demo: DemoStreamingSource): List<StreamingProvider> = listOf(demo)

    @Provides
    @Singleton
    fun provideSearchAggregator(providers: List<SearchProvider>): SearchAggregator = SearchAggregator(providers)

    @Provides
    @Singleton
    fun provideMetadataRegistry(providers: List<AnimeMetadataProvider>): MetadataRegistry =
        MetadataRegistry(providers)

    @Provides
    @Singleton
    fun provideEpisodeRegistry(providers: List<EpisodeProvider>): EpisodeRegistry = EpisodeRegistry(providers)

    @Provides
    @Singleton
    fun provideStreamingRegistry(providers: List<StreamingProvider>): StreamingRegistry =
        StreamingRegistry(providers)
}
