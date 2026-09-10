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
        val completion = completions.getOrPut(handle) { CompletableDeferred() }
        if (operations.activePublication() == handle) {
            completion.await()
        } else {
            completions.remove(handle, completion)
        }
        return true
    }

    /**
     * The publication and the transition that records its outcome run under one [NonCancellable] block, so a
     * cancelled caller can never leave a durable Candidate that the runtime does not know about. Caller
     * cancellation therefore does not reach the catch below; it stays as a safety net for a port that raises
     * cancellation on its own, and it releases the lease before rethrowing.
     */
    private suspend fun publish(start: AutosaveStart.Started): AutosaveRequestResult {
        completions.getOrPut(start.handle) { CompletableDeferred() }
        return try {
            withContext(NonCancellable) {
                operations.completePublication(
                    start.handle,
                    recoveryRecord.publishCandidate(start.expected, start.capture.document),
                )
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { operations.releasePublication(start.handle) }
            throw cancelled
        } finally {
            completions.remove(start.handle)?.complete(Unit)
        }
    }
}
