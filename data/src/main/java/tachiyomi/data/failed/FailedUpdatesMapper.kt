package tachiyomi.data.failed

import tachiyomi.domain.failed.model.FailedUpdate

val failedUpdatesMapper: (Long, String, String) -> FailedUpdate = { mangaId, errorMessage, simplifiedErrorMessage ->
    FailedUpdate(
        mangaId = mangaId,
        errorMessage = errorMessage,
        simplifiedErrorMessage = simplifiedErrorMessage,
    )
}
