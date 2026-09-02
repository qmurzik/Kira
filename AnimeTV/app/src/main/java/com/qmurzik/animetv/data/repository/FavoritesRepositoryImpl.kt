package com.qmurzik.animetv.data.repository

import com.qmurzik.animetv.data.local.db.FavoriteDao
import com.qmurzik.animetv.data.local.db.FavoriteEntity
import com.qmurzik.animetv.domain.model.AnimeSummary
import com.qmurzik.animetv.domain.repository.FavoriteItem
import com.qmurzik.animetv.domain.repository.FavoritesRepository
import com.qmurzik.animetv.domain.repository.FavoritesSortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val dao: FavoriteDao,
) : FavoritesRepository {

    override fun observeFavorites(sortOrder: FavoritesSortOrder): Flow<List<FavoriteItem>> {
        val flow = when (sortOrder) {
            FavoritesSortOrder.RECENTLY_ADDED -> dao.observeAllByRecent()
            FavoritesSortOrder.TITLE_AZ -> dao.observeAllByTitle()
            FavoritesSortOrder.YEAR -> dao.observeAllByYear()
        }
        return flow.map { list -> list.map { it.toFavoriteItem() } }
    }

    override fun isFavorite(animeId: String): Flow<Boolean> = dao.observeIsFavorite(animeId)

    override suspend fun add(anime: AnimeSummary) {
        dao.upsert(
            FavoriteEntity(
                animeId = anime.id,
                title = anime.title,
                titleEnglish = anime.titleEnglish,
                posterUrl = anime.posterUrl,
                year = anime.year,
                rating = anime.rating,
                format = anime.format,
                sourceRefs = anime.sourceRefs,
                addedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun remove(animeId: String) {
        dao.delete(animeId)
    }

    override suspend fun toggle(anime: AnimeSummary) {
        if (isFavorite(anime.id).first()) remove(anime.id) else add(anime)
    }
}

private fun FavoriteEntity.toFavoriteItem() = FavoriteItem(
    anime = AnimeSummary(
        id = animeId,
        title = title,
        titleEnglish = titleEnglish,
        posterUrl = posterUrl,
        year = year,
        rating = rating,
        format = format,
        sourceRefs = sourceRefs,
    ),
    addedAtEpochMs = addedAtEpochMs,
)
