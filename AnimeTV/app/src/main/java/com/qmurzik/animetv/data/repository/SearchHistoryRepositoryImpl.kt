package com.qmurzik.animetv.data.repository

import com.qmurzik.animetv.data.local.db.SearchHistoryDao
import com.qmurzik.animetv.data.local.db.SearchHistoryEntity
import com.qmurzik.animetv.domain.repository.SearchHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_ENTRIES = 30

@Singleton
class SearchHistoryRepositoryImpl @Inject constructor(
    private val dao: SearchHistoryDao,
) : SearchHistoryRepository {

    override fun observeRecentQueries(limit: Int): Flow<List<String>> = dao.observeRecent(limit)

    override suspend fun record(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        dao.upsert(SearchHistoryEntity(query = trimmed, searchedAtEpochMs = System.currentTimeMillis()))
        dao.trimTo(MAX_ENTRIES)
    }

    override suspend fun clear() = dao.clear()
}
