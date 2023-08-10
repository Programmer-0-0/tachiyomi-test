package tachiyomi.domain.failed.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.failed.model.FailedUpdate
import tachiyomi.domain.manga.model.Manga

interface FailedUpdatesRepository {
    suspend fun getFailedUpdates(): Flow<List<FailedUpdate>>

    fun getFailedUpdatesCount(): Flow<Long>

    suspend fun removeFailedUpdatesByMangaIds(mangaIds: List<Long>)

    suspend fun removeAllFailedUpdates()

    suspend fun insert(manga: Manga, errorMessage: String?)
}
