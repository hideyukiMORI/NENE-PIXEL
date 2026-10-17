package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveStateToken

/**
 * The pure decision part of the ADR 0018 autosave scheduler: given the latest [AutosaveObservation]
 * and a monotonic nanosecond reading, it answers how long to wait before the next
 * `publishLatestCapture` request. It owns no scope, no timer, and no persistence workflow.
 */
internal data class AutosaveSchedule private constructor(
    val observation: AutosaveObservation,
    val quietDeadlineNanos: Long?,
    val capDeadlineNanos: Long?,
    private val request: RequestState,
) {
    val suspended: Boolean
        get() = request.suspension != Suspension.None

    /**
     * Folds one sample into the schedule. A new capture restarts the quiet window; a completed
     * publication restarts the quiet window; a lifecycle flush makes the request due immediately;
     * a changed gate releases a suspended schedule.
     */
    fun observed(
        sample: AutosaveObservation,
        nowNanos: Long,
        policy: AutosavePolicy,
    ): AutosaveSchedule =
        when {
            !sample.states.requestable -> {
                AutosaveSchedule(
                    sample,
                    null,
                    null,
                    requestAfter(sample),
                )
            }

            sample.flushes != observation.flushes -> {
                AutosaveSchedule(
                    sample,
                    nowNanos,
                    nowNanos,
                    requestAfter(sample, Suspension.None),
                )
            }

            else -> {
                retimed(sample, nowNanos, policy)
            }
        }

    /** Nanoseconds to wait before the next request, `0` when it is due, `null` when none is wanted. */
    fun dueNanos(nowNanos: Long): Long? =
        when {
            !observation.states.requestable || suspended -> null
            else -> (deadlineNanos() - nowNanos).coerceAtLeast(0L)
        }

    /**
     * Applies a typed `Deferred` result: a user operation holds the lease, so the request is retried
     * after one quiet window and the latency cap is pushed to at least that retry.
     */
    fun deferred(
        nowNanos: Long,
        policy: AutosavePolicy,
    ): AutosaveSchedule {
        val retryNanos = nowNanos + policy.quietNanos
        return copy(
            quietDeadlineNanos = retryNanos,
            capDeadlineNanos = capDeadlineNanos?.coerceAtLeast(retryNanos) ?: retryNanos,
        )
    }

    /** Stops requesting until the persistence-operation gate changes or a lifecycle flush arrives. */
    fun suspendedUntilGate(): AutosaveSchedule = copy(request = request.updated(suspension = Suspension.Gate))

    fun startedRequest(): AutosaveSchedule =
        copy(request = request.updated(requestedStateToken = observation.states.pending))

    fun applied(
        result: AutosaveRequestResult,
        requestedStateToken: AutosaveStateToken,
        nowNanos: Long,
        policy: AutosavePolicy,
    ): AutosaveSchedule {
        if (request.requestedStateToken != requestedStateToken) return this
        return when (result) {
            is AutosaveRequestResult.Published,
            AutosaveRequestResult.NoCapture,
            -> {
                copy(request = request.updated(Suspension.Observation, null))
            }

            AutosaveRequestResult.Deferred -> {
                copy(request = request.updated(requestedStateToken = null)).deferred(nowNanos, policy)
            }

            AutosaveRequestResult.OfferPending,
            AutosaveRequestResult.Unavailable,
            AutosaveRequestResult.Stale,
            AutosaveRequestResult.GenerationExhausted,
            AutosaveRequestResult.IdentityExhausted,
            is AutosaveRequestResult.Failed,
            is AutosaveRequestResult.Uncertain,
            -> {
                copy(request = request.updated(Suspension.Gate, null))
            }
        }
    }

    private fun retimed(
        sample: AutosaveObservation,
        nowNanos: Long,
        policy: AutosavePolicy,
    ): AutosaveSchedule {
        val previousStates = observation.states
        val currentStates = sample.states
        val published = currentStates.published != previousStates.published
        val completed = previousStates.publishing != null && currentStates.publishing == null
        val captured = currentStates.pending != previousStates.pending
        return AutosaveSchedule(
            observation = sample,
            quietDeadlineNanos =
                deadlineAfterChange(
                    changed = published || completed || captured,
                    current = quietDeadlineNanos,
                    nowNanos = nowNanos,
                    intervalNanos = policy.quietNanos,
                ),
            capDeadlineNanos =
                deadlineAfterChange(
                    changed = previousDeadlineConsumed(previousStates, currentStates, published),
                    current = capDeadlineNanos,
                    nowNanos = nowNanos,
                    intervalNanos = policy.latencyCapNanos,
                ),
            request = requestAfter(sample),
        )
    }

    private fun suspensionAfter(sample: AutosaveObservation): Suspension =
        when (request.suspension) {
            Suspension.None -> Suspension.None
            Suspension.Gate -> if (sample.gate == observation.gate) Suspension.Gate else Suspension.None
            Suspension.Observation -> if (sample == observation) Suspension.Observation else Suspension.None
        }

    private fun requestAfter(
        sample: AutosaveObservation,
        suspension: Suspension = suspensionAfter(sample),
    ): RequestState =
        request.updated(suspension, if (sample.gate == observation.gate) request.requestedStateToken else null)

    private fun deadlineNanos(): Long =
        listOfNotNull(quietDeadlineNanos, capDeadlineNanos).minOrNull() ?: Long.MAX_VALUE

    companion object {
        fun idle(): AutosaveSchedule =
            AutosaveSchedule(
                observation =
                    AutosaveObservation(
                        states = AutosaveStateObservation(null, null, null),
                        gate = null,
                        flushes = 0L,
                    ),
                quietDeadlineNanos = null,
                capDeadlineNanos = null,
                request = RequestState(Suspension.None, null),
            )
    }

    private enum class Suspension { None, Gate, Observation }

    private data class RequestState(
        val suspension: Suspension,
        val requestedStateToken: AutosaveStateToken?,
    ) {
        fun updated(
            suspension: Suspension = this.suspension,
            requestedStateToken: AutosaveStateToken? = this.requestedStateToken,
        ): RequestState =
            if (this.suspension == suspension && this.requestedStateToken == requestedStateToken) {
                this
            } else {
                RequestState(suspension, requestedStateToken)
            }
    }
}

private fun previousDeadlineConsumed(
    previous: AutosaveStateObservation,
    current: AutosaveStateObservation,
    publishedChanged: Boolean,
): Boolean {
    val previousPending = previous.pending
    if (previousPending == null || previousPending == current.pending) return false
    return previousPending == current.publishing || publishedConsumed(previousPending, current, publishedChanged)
}

private fun publishedConsumed(
    previousPending: AutosaveStateToken,
    current: AutosaveStateObservation,
    publishedChanged: Boolean,
): Boolean = publishedChanged && previousPending == current.published

private fun deadlineAfterChange(
    changed: Boolean,
    current: Long?,
    nowNanos: Long,
    intervalNanos: Long,
): Long = if (changed || current == null) nowNanos + intervalNanos else current
