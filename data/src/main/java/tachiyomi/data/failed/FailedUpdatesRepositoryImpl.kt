package tachiyomi.data.failed

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.failed.model.FailedUpdate
import tachiyomi.domain.failed.repository.FailedUpdatesRepository
import tachiyomi.domain.manga.model.Manga

class FailedUpdatesRepositoryImpl(
    private val handler: DatabaseHandler,
) : FailedUpdatesRepository {
    override suspend fun getFailedUpdates(): Flow<List<FailedUpdate>> {
        return handler.subscribeToList { failed_updatesQueries.getFailedUpdates(failedUpdatesMapper) }
    }

    override fun getFailedUpdatesCount(): Flow<Long> {
        return handler.subscribeToOne { failed_updatesQueries.getFailedUpdatesCount() }
    }

    override suspend fun removeFailedUpdatesByMangaIds(mangaIds: List<Long>) {
        try {
            handler.await { failed_updatesQueries.removeFailedUpdatesByMangaIds(mangaIds) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override suspend fun removeAllFailedUpdates() {
        try {
            handler.await { failed_updatesQueries.removeAllFailedUpdates() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
    override suspend fun insert(manga: Manga, errorMessage: String?) {
        handler.await(inTransaction = true) {
            failed_updatesQueries.insert(
                mangaId = manga.id,
                errorMessage = errorMessage,
            )
        }
    }
}
