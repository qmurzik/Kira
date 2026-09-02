package com.qmurzik.animetv.domain.source

import com.qmurzik.animetv.domain.model.AnimeDetails
import com.qmurzik.animetv.domain.model.AnimeSummary
import com.qmurzik.animetv.domain.model.PlaybackSource
import com.qmurzik.animetv.domain.model.Season
import com.qmurzik.animetv.domain.model.SourceRef
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Fans a search query out to every registered [SearchProvider] in parallel, then merges the
 * results into one deduplicated list keyed by [TitleNormalizer]. A provider that errors or
 * times out simply contributes nothing - it never fails the whole search (see item 4/13:
 * "Source A -> if it fails -> Source B -> ... "; for *search* specifically we don't need to
 * pick just one, we want the union of everything that answered).
 */
class SearchAggregator(private val providers: List<SearchProvider>) {

    suspend fun search(query: String): AggregatedSearchResult = coroutineScope {
        val deferred = providers.filter { it.capabilities.search }.map { provider ->
            async { provider.id to provider.search(query) }
        }
        val results = deferred.map { it.await() }

        val merged = LinkedHashMap<String, AnimeSummary>()
        val failures = mutableListOf<SourceResult.Failure>()

        for ((_, result) in results) {
            when (result) {
                is SourceResult.Success -> for (summary in result.value) {
                    val key = TitleNormalizer.normalize(summary.title)
                    val existing = merged[key]
                    merged[key] = if (existing == null) {
                        summary.copy(id = TitleNormalizer.idFor(summary.title))
                    } else {
                        // Keep the richer record but union the source refs so episode/stream
                        // lookups can still fall back across every provider that matched.
                        existing.copy(
                            posterUrl = existing.posterUrl ?: summary.posterUrl,
                            rating = existing.rating ?: summary.rating,
                            year = existing.year ?: summary.year,
                            sourceRefs = existing.sourceRefs + summary.sourceRefs,
                        )
                    }
                }
                is SourceResult.Failure -> failures += result
            }
        }

        AggregatedSearchResult(merged.values.toList(), failures)
    }
}

data class AggregatedSearchResult(
    val results: List<AnimeSummary>,
    val failures: List<SourceResult.Failure>,
)

/**
 * Walks a title's [SourceRef]s in order and returns the first provider that succeeds -
 * the "Source A -> if it fails -> Source B -> Source C" fallback chain from the spec,
 * shared by metadata, episode-list and streaming lookups alike.
 */
class FallbackChain<P : SourceProvider>(private val providers: List<P>) {

    private fun providersFor(refs: List<SourceRef>): List<Pair<P, SourceRef>> =
        refs.mapNotNull { ref -> providers.find { it.id == ref.sourceId }?.let { it to ref } }

    suspend fun <T> resolve(
        refs: List<SourceRef>,
        call: suspend (P, SourceRef) -> SourceResult<T>,
    ): SourceResult<T> {
        val candidates = providersFor(refs)
        if (candidates.isEmpty()) return SourceResult.Failure(SourceError.Unsupported, "none")

        var lastFailure: SourceResult.Failure? = null
        for ((provider, ref) in candidates) {
            when (val result = call(provider, ref)) {
                is SourceResult.Success -> return result
                is SourceResult.Failure -> lastFailure = result
            }
        }
        return lastFailure ?: SourceResult.Failure(SourceError.NotFound, "none")
    }
}

class MetadataRegistry(providers: List<AnimeMetadataProvider>) {
    private val chain = FallbackChain(providers.filter { it.capabilities.metadata })

    suspend fun getDetails(refs: List<SourceRef>): SourceResult<AnimeDetails> =
        chain.resolve(refs) { provider, ref -> provider.getDetails(ref.externalId) }
}

class EpisodeRegistry(providers: List<EpisodeProvider>) {
    private val chain = FallbackChain(providers.filter { it.capabilities.episodes })

    suspend fun getSeasons(refs: List<SourceRef>): SourceResult<List<Season>> =
        chain.resolve(refs) { provider, ref -> provider.getSeasons(ref.externalId) }
}

class StreamingRegistry(providers: List<StreamingProvider>) {
    private val chain = FallbackChain(providers.filter { it.capabilities.streaming })

    suspend fun getStreams(
        refs: List<SourceRef>,
        seasonNumber: Int,
        episodeNumber: Int,
    ): SourceResult<PlaybackSource> =
        chain.resolve(refs) { provider, ref -> provider.getStreams(ref.externalId, seasonNumber, episodeNumber) }
}
