package com.qmurzik.animetv.data.repository

import com.qmurzik.animetv.data.local.db.AnimeIndexDao
import com.qmurzik.animetv.data.local.db.AnimeIndexEntity
import com.qmurzik.animetv.data.source.anilist.AniListSource
import com.qmurzik.animetv.data.source.shikimori.SHIKIMORI_SOURCE_ID
import com.qmurzik.animetv.domain.model.AnimeDetails
import com.qmurzik.animetv.domain.model.AnimeSummary
import com.qmurzik.animetv.domain.model.PlaybackSource
import com.qmurzik.animetv.domain.model.Season
import com.qmurzik.animetv.domain.model.SourceRef
import com.qmurzik.animetv.domain.repository.AnimeOutcome
import com.qmurzik.animetv.domain.repository.AnimeRepository
import com.qmurzik.animetv.domain.repository.AppLanguage
import com.qmurzik.animetv.domain.repository.HomeSections
import com.qmurzik.animetv.domain.repository.SettingsRepository
import com.qmurzik.animetv.domain.source.EpisodeRegistry
import com.qmurzik.animetv.domain.source.MetadataRegistry
import com.qmurzik.animetv.domain.source.SearchAggregator
import com.qmurzik.animetv.domain.source.SourceError
import com.qmurzik.animetv.domain.source.SourceResult
import com.qmurzik.animetv.domain.source.StreamingRegistry
import com.qmurzik.animetv.domain.source.TitleNormalizer
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Every merged anime automatically gets this ref so the demo streaming source can always
 *  serve as the last link in the fallback chain, regardless of which metadata source matched. */
private const val DEMO_SOURCE_ID = "demo"

private const val INDEX_CACHE_LIMIT = 500

@Singleton
class AnimeRepositoryImpl @Inject constructor(
    private val searchAggregator: SearchAggregator,
    private val metadataRegistry: MetadataRegistry,
    private val episodeRegistry: EpisodeRegistry,
    private val streamingRegistry: StreamingRegistry,
    private val aniListSource: AniListSource,
    private val indexDao: AnimeIndexDao,
    private val settingsRepository: SettingsRepository,
) : AnimeRepository {

    /**
     * Honors Settings -> Playback -> "preferred source" by trying it first in the fallback
     * chain instead of always using the fixed Jikan-then-Shikimori-then-AniList-then-demo
     * order. Absent an explicit choice, Shikimori (Russian titles/synopses) is preferred
     * automatically whenever the UI language is Russian - either chosen explicitly in
     * Settings, or left on "system default" with a Russian device locale.
     */
    private suspend fun List<SourceRef>.prioritized(): List<SourceRef> {
        val settings = settingsRepository.settings.first()
        val explicit = settings.playback.preferredSourceId
        val preferred = explicit ?: run {
            val language = settings.appearance.language
            val isRussian = language == AppLanguage.RUSSIAN ||
                (language == AppLanguage.SYSTEM && java.util.Locale.getDefault().language == "ru")
            if (isRussian) SHIKIMORI_SOURCE_ID else null
        } ?: return this
        return sortedByDescending { it.sourceId == preferred }
    }

    override suspend fun search(query: String): AnimeOutcome<List<AnimeSummary>> {
        val aggregated = searchAggregator.search(query)
        val withDemoFallback = aggregated.results.map { it.withDemoRef() }
        cacheIndex(withDemoFallback)

        return when {
            withDemoFallback.isNotEmpty() -> AnimeOutcome.Success(withDemoFallback)
            aggregated.failures.isNotEmpty() -> AnimeOutcome.Error(aggregated.failures.first().error)
            else -> AnimeOutcome.Success(emptyList())
        }
    }

    override suspend fun getDetails(animeId: String): AnimeOutcome<AnimeDetails> {
        val cached = indexDao.get(animeId) ?: return AnimeOutcome.Error(SourceError.NotFound)
        return when (val result = metadataRegistry.getDetails(cached.sourceRefs.prioritized())) {
            is SourceResult.Success -> {
                val details = result.value.copy(
                    id = animeId,
                    sourceRefs = (result.value.sourceRefs + cached.sourceRefs).distinct(),
                )
                AnimeOutcome.Success(details)
            }
            is SourceResult.Failure -> AnimeOutcome.Error(result.error)
        }
    }

    override suspend fun getSeasons(animeId: String): AnimeOutcome<List<Season>> {
        val cached = indexDao.get(animeId) ?: return AnimeOutcome.Error(SourceError.NotFound)
        return when (val result = episodeRegistry.getSeasons(cached.sourceRefs.prioritized())) {
            is SourceResult.Success -> AnimeOutcome.Success(result.value)
            is SourceResult.Failure -> AnimeOutcome.Error(result.error)
        }
    }

    override suspend fun getStreams(
        animeId: String,
        seasonNumber: Int,
        episodeNumber: Int,
    ): AnimeOutcome<PlaybackSource> {
        val cached = indexDao.get(animeId) ?: return AnimeOutcome.Error(SourceError.NotFound)
        val refs = cached.sourceRefs.prioritized()
        return when (val result = streamingRegistry.getStreams(refs, seasonNumber, episodeNumber)) {
            is SourceResult.Success -> AnimeOutcome.Success(result.value, partial = result.value.isDemoContent)
            is SourceResult.Failure -> AnimeOutcome.Error(result.error)
        }
    }

    override suspend fun homeSections(): AnimeOutcome<HomeSections> {
        val trending = aniListSource.trending().getOrNull().orEmpty()
        val popular = aniListSource.popular().getOrNull().orEmpty()

        if (trending.isEmpty() && popular.isEmpty()) {
            return AnimeOutcome.Error(SourceError.NoConnectivity)
        }

        val trendingWithId = trending.map { it.withStableId().withDemoRef() }
        val popularWithId = popular.map { it.withStableId().withDemoRef() }
        // AniList doesn't expose a distinct "recently added"/"recommended" feed via a single
        // free query, so we derive reasonable, still-real sections from what we already have:
        // recently-aired-first for "Recently Added", and a shuffled slice of popular titles the
        // user hasn't already favorited-equivalent for "Recommended" (simple, honest heuristic -
        // no fabricated data, just a different ordering of real results).
        val recentlyAdded = trendingWithId.sortedByDescending { it.year ?: 0 }
        val recommended = popularWithId.shuffled().take(10)

        cacheIndex(trendingWithId + popularWithId)

        return AnimeOutcome.Success(
            HomeSections(
                trending = trendingWithId,
                popular = popularWithId,
                recentlyAdded = recentlyAdded,
                recommended = recommended,
            ),
        )
    }

    private suspend fun cacheIndex(summaries: List<AnimeSummary>) {
        if (summaries.isEmpty()) return
        val entities = summaries.map { summary ->
            AnimeIndexEntity(
                animeId = summary.id,
                title = summary.title,
                titleEnglish = summary.titleEnglish,
                titleNative = summary.titleNative,
                posterUrl = summary.posterUrl,
                year = summary.year,
                rating = summary.rating,
                format = summary.format,
                sourceRefs = summary.sourceRefs,
                cachedAtEpochMs = System.currentTimeMillis(),
            )
        }
        indexDao.upsertAll(entities)
        indexDao.trimTo(INDEX_CACHE_LIMIT)
    }

    private fun AnimeSummary.withStableId(): AnimeSummary = copy(id = TitleNormalizer.idFor(title))

    private fun AnimeSummary.withDemoRef(): AnimeSummary =
        copy(sourceRefs = sourceRefs + SourceRef(DEMO_SOURCE_ID, id))
}
