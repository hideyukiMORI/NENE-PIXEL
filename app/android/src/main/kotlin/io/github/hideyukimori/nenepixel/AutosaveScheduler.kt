package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.application.persistence.EditorPersistenceWorkflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
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
    private val scheduleState: MutableStateFlow<AutosaveSchedule> = MutableStateFlow(AutosaveSchedule.idle())

    private val observations: Flow<AutosaveObservation> =
        combine(workflow.autosave, workflow.operation, flushes) { autosave, operation, flushCount ->
            AutosaveObservation(
                states =
                    AutosaveStateObservation(
                        pending = autosave.pendingStateToken,
                        published = autosave.publishedStateToken,
                        publishing = autosave.publishingStateToken,
                    ),
                gate = operation,
                flushes = flushCount,
            )
        }

    fun launchIn(scope: CoroutineScope): Job = scope.launch { schedule() }

    /** Requests an immediate publication of any pending capture; the ADR 0018 `ON_STOP` flush. */
    fun flush() {
        flushes.update { count -> count + 1L }
    }

    private suspend fun schedule(): Unit =
        coroutineScope {
            launch(start = CoroutineStart.UNDISPATCHED) {
                observations.collect { sample ->
                    scheduleState.update { state -> state.observed(sample, clockNanos(), policy) }
                }
            }

            while (true) {
                val state = scheduleState.value
                val due = state.dueNanos(clockNanos())
                when {
                    due == null -> {
                        awaitChange(state)
                    }

                    due > 0L -> {
                        awaitChange(state, due)
                    }

                    else -> {
                        val requestedStateToken = requireNotNull(state.observation.states.pending)
                        if (!scheduleState.compareAndSet(state, state.startedRequest())) continue
                        val result = workflow.publishLatestCapture()
                        scheduleState.update { current ->
                            current.applied(result, requestedStateToken, clockNanos(), policy)
                        }
                    }
                }
            }
        }

    private suspend fun awaitChange(previous: AutosaveSchedule): AutosaveSchedule =
        scheduleState.first { state -> state != previous }

    private suspend fun awaitChange(
        previous: AutosaveSchedule,
        waitNanos: Long,
    ): AutosaveSchedule = withTimeoutOrNull(waitNanos.nanoseconds) { awaitChange(previous) } ?: previous
}
