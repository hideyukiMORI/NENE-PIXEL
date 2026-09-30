package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.editor.ReferenceImagePickStart
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeReferenceImageOperations
import kotlinx.coroutines.CancellationException

internal class PersistenceReferenceImageFlow(
    private val operations: RuntimeReferenceImageOperations,
    private val picker: ReferenceImagePort,
    private val autosave: PersistenceAutosaveFlow,
) {
    suspend fun pick(): PersistenceRequestResult = autosave.retryAfterPublication { attempt() }

    private suspend fun attempt(): PersistenceRequestResult =
        when (val start = operations.beginPick()) {
            is ReferenceImagePickStart.Started -> pick(start)
            ReferenceImagePickStart.Busy -> PersistenceRequestResult.Busy
            ReferenceImagePickStart.RecoveryUnavailable -> PersistenceRequestResult.RecoveryUnavailable
            ReferenceImagePickStart.IdentityExhausted -> identityExhaustedResult()
        }

    private suspend fun pick(start: ReferenceImagePickStart.Started): PersistenceRequestResult =
        try {
            operations.completePick(start.handle, picker.pick())
        } catch (cancelled: CancellationException) {
            operations.cancelPick(start.handle)
            throw cancelled
        }
}
