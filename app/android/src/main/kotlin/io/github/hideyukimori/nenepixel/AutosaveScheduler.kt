package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.EditorPersistenceWorkflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.nanoseconds

/**
 * The platform half of the ADR 0018 autosave contract. It owns the clock and the waiting; the
 * decision itself lives in [AutosaveSchedule]. Exactly one `publishLatestCapture` request is
 * outstanding at a time, and the request is issued on the scope it was launched in, so it survives
 * configuration changes.
 */
internal class AutosaveScheduler(
    private val workflow: EditorPersistenceWorkflow,
    private val policy: AutosavePolicy = AutosavePolicy.DEFAULT,
    private val clockNanos: () -> Long = System::nanoTime,
) {
    private val flushes: MutableStateFlow<Long> = MutableStateFlow(0L)

    private val observations: Flow<AutosaveObservation> =
        combine(workflow.autosave, workflow.operation, flushes) { autosave, operation, flushCount ->
            AutosaveObservation(
                pendingRevision = autosave.pendingRevision,
                publishedRevision = autosave.publishedRevision,
                gate = operation,
                flushes = flushCount,
            )
        }

    fun launchIn(scope: CoroutineScope): Job = scope.launch { schedule() }

    /** Requests an immediate publication of any pending capture; the ADR 0018 `ON_STOP` flush. */
    fun flush() {
        flushes.update { count -> count + 1L }
    }

    private suspend fun schedule() {
        var state = AutosaveSchedule.idle()
        var sample = observations.first()
        while (true) {
            state = state.observed(sample, clockNanos(), policy)
            val due = state.dueNanos(clockNanos())
            when {
                due == null -> {
                    sample = awaitChange(sample)
                }

                due > 0L -> {
                    sample = awaitChange(sample, due)
                }

                else -> {
                    state = state.applied(workflow.publishLatestCapture(), clockNanos())
                    sample = observations.first()
                }
            }
        }
    }

    private suspend fun awaitChange(previous: AutosaveObservation): AutosaveObservation =
        observations.first { sample -> sample != previous }

    private suspend fun awaitChange(
        previous: AutosaveObservation,
        waitNanos: Long,
    ): AutosaveObservation = withTimeoutOrNull(waitNanos.nanoseconds) { awaitChange(previous) } ?: previous

    private fun AutosaveSchedule.applied(
        result: AutosaveRequestResult,
        nowNanos: Long,
    ): AutosaveSchedule =
        when (result) {
            is AutosaveRequestResult.Published -> this

            AutosaveRequestResult.Deferred -> deferred(nowNanos, policy)

            AutosaveRequestResult.NoCapture,
            AutosaveRequestResult.OfferPending,
            AutosaveRequestResult.Unavailable,
            AutosaveRequestResult.Stale,
            AutosaveRequestResult.GenerationExhausted,
            AutosaveRequestResult.IdentityExhausted,
            is AutosaveRequestResult.Failed,
            is AutosaveRequestResult.Uncertain,
            -> suspendedUntilGate()
        }
}
