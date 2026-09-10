package io.github.hideyukimori.nenepixel

/**
 * The pure decision part of the ADR 0018 autosave scheduler: given the latest [AutosaveObservation]
 * and a monotonic nanosecond reading, it answers how long to wait before the next
 * `publishLatestCapture` request. It owns no scope, no timer, and no persistence workflow.
 */
internal data class AutosaveSchedule(
    val observation: AutosaveObservation,
    val quietDeadlineNanos: Long?,
    val capDeadlineNanos: Long?,
    val suspended: Boolean,
) {
    /**
     * Folds one sample into the schedule. A new capture restarts the quiet window; a completed
     * publication restarts both windows; a lifecycle flush makes the request due immediately; a
     * changed gate releases a suspended schedule.
     */
    fun observed(
        sample: AutosaveObservation,
        nowNanos: Long,
        policy: AutosavePolicy,
    ): AutosaveSchedule =
        when {
            sample.pendingRevision == null -> AutosaveSchedule(sample, null, null, false)
            sample.flushes != observation.flushes -> AutosaveSchedule(sample, nowNanos, nowNanos, false)
            else -> retimed(sample, nowNanos, policy)
        }

    /** Nanoseconds to wait before the next request, `0` when it is due, `null` when none is wanted. */
    fun dueNanos(nowNanos: Long): Long? =
        when {
            observation.pendingRevision == null || suspended -> null
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
    fun suspendedUntilGate(): AutosaveSchedule = copy(suspended = true)

    private fun retimed(
        sample: AutosaveObservation,
        nowNanos: Long,
        policy: AutosavePolicy,
    ): AutosaveSchedule {
        val published = sample.publishedRevision != observation.publishedRevision
        val captured = sample.pendingRevision != observation.pendingRevision
        return AutosaveSchedule(
            observation = sample,
            quietDeadlineNanos =
                if (published || captured || quietDeadlineNanos == null) {
                    nowNanos + policy.quietNanos
                } else {
                    quietDeadlineNanos
                },
            capDeadlineNanos =
                if (published || capDeadlineNanos == null) {
                    nowNanos + policy.latencyCapNanos
                } else {
                    capDeadlineNanos
                },
            suspended = suspended && sample.gate == observation.gate,
        )
    }

    private fun deadlineNanos(): Long =
        listOfNotNull(quietDeadlineNanos, capDeadlineNanos).minOrNull() ?: Long.MAX_VALUE

    companion object {
        fun idle(): AutosaveSchedule =
            AutosaveSchedule(
                observation = AutosaveObservation(null, null, null, 0L),
                quietDeadlineNanos = null,
                capDeadlineNanos = null,
                suspended = false,
            )
    }
}
