package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.editor.AutosaveStart
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeAutosaveOperations
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

internal class PersistenceAutosaveFlow(
    private val operations: RuntimeAutosaveOperations,
    private val recoveryRecord: RecoveryRecordPort,
) {
    private val completions: ConcurrentHashMap<PersistenceOperationHandle, CompletableDeferred<Unit>> =
        ConcurrentHashMap()

    suspend fun publishLatestCapture(): AutosaveRequestResult =
        when (val start = operations.beginPublication()) {
            is AutosaveStart.Started -> publish(start)
            AutosaveStart.NoCapture -> AutosaveRequestResult.NoCapture
            AutosaveStart.Deferred -> AutosaveRequestResult.Deferred
            AutosaveStart.OfferPending -> AutosaveRequestResult.OfferPending
            AutosaveStart.Unavailable -> AutosaveRequestResult.Unavailable
            AutosaveStart.IdentityExhausted -> AutosaveRequestResult.IdentityExhausted
        }

    suspend fun retryAfterPublication(attempt: suspend () -> PersistenceRequestResult): PersistenceRequestResult {
        val first = attempt()
        return if (first == PersistenceRequestResult.Busy && awaitActivePublication()) attempt() else first
    }

    private suspend fun awaitActivePublication(): Boolean {
        val handle = operations.activePublication() ?: return false
        completions[handle]?.await()
        return true
    }

    private suspend fun publish(start: AutosaveStart.Started): AutosaveRequestResult {
        completions[start.handle] = CompletableDeferred()
        return try {
            finish(start, recoveryRecord.publishCandidate(start.expected, start.capture.document))
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { operations.releasePublication(start.handle) }
            throw cancelled
        } finally {
            completions.remove(start.handle)?.complete(Unit)
        }
    }

    private suspend fun finish(
        start: AutosaveStart.Started,
        outcome: RecoveryPublicationOutcome,
    ): AutosaveRequestResult = withContext(NonCancellable) { operations.completePublication(start.handle, outcome) }
}
