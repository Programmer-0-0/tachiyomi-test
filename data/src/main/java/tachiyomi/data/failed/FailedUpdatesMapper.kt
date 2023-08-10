package tachiyomi.data.failed

import tachiyomi.domain.failed.model.FailedUpdate

val failedUpdatesMapper: (Long, String) -> FailedUpdate = { mangaId, errorMessage ->
    FailedUpdate(
        mangaId = mangaId,
        errorMessage = errorMessage,
    )
}
