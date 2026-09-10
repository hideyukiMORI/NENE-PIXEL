package io.github.hideyukimori.nenepixel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Host coverage of the pure ADR 0018 scheduling rules. The clock is an explicit nanosecond value, so
 * the quiet window, the latency cap, the lifecycle flush, the deferred retry, and the offer-pending
 * stop are all decidable without a runtime, a dispatcher, or a real delay.
 */
internal class AutosaveScheduleTest {
    @Test
    fun quietWindowBecomesDueOneQuietWindowAfterTheLatestCapture() {
        val captured = idle().observed(capture(revision = 1L), START, POLICY)

        assertEquals(QUIET, captured.dueNanos(START))
        assertEquals(1L, captured.dueNanos(START + QUIET - 1L))
        assertEquals(0L, captured.dueNanos(START + QUIET))
    }

    @Test
    fun newerCaptureRestartsTheQuietWindowWithoutMovingTheLatencyCap() {
        val first = idle().observed(capture(revision = 1L), START, POLICY)
        val second = first.observed(capture(revision = 2L), START + HALF_QUIET, POLICY)

        assertEquals(QUIET, second.dueNanos(START + HALF_QUIET))
        assertEquals(START + CAP, second.capDeadlineNanos)
    }

    @Test
    fun latencyCapBecomesDueWhileCapturesKeepRestartingTheQuietWindow() {
        var schedule = idle()
        var revision = 0L
        var now = START
        while (now < START + CAP) {
            revision += 1L
            schedule = schedule.observed(capture(revision), now, POLICY)
            now += HALF_QUIET
        }

        assertEquals(0L, schedule.dueNanos(START + CAP))
    }

    @Test
    fun lifecycleFlushMakesAPendingCaptureDueImmediatelyAndIgnoresAnEmptyOne() {
        val captured = idle().observed(capture(revision = 1L), START, POLICY)
        val flushed = captured.observed(capture(revision = 1L, flushes = 1L), START + HALF_QUIET, POLICY)

        assertEquals(0L, flushed.dueNanos(START + HALF_QUIET))
        assertNull(idle().observed(capture(revision = null, flushes = 1L), START, POLICY).dueNanos(START))
    }

    @Test
    fun deferredResultRetriesAfterOneQuietWindowInsteadOfSpinningOnAnElapsedCap() {
        val capped = idle().observed(capture(revision = 1L), START, POLICY)
        val deferred = capped.deferred(START + CAP, POLICY)

        assertEquals(QUIET, deferred.dueNanos(START + CAP))
        assertEquals(0L, deferred.dueNanos(START + CAP + QUIET))
    }

    @Test
    fun offerPendingStopsRequestingUntilThePersistenceOperationGateChanges() {
        val suspended =
            idle()
                .observed(capture(revision = 1L), START, POLICY)
                .suspendedUntilGate()

        assertNull(suspended.dueNanos(START + CAP))

        val sameGate = suspended.observed(capture(revision = 2L), START + CAP, POLICY)
        assertNull(sameGate.dueNanos(START + CAP))

        val changedGate = sameGate.observed(capture(revision = 2L, gate = "resolved"), START + CAP, POLICY)
        assertEquals(0L, changedGate.dueNanos(START + CAP))
    }

    @Test
    fun completedPublicationRestartsBothWindowsAndClearsWhenNothingIsPending() {
        val captured = idle().observed(capture(revision = 2L), START, POLICY)
        val publishedWithNewerCapture =
            captured.observed(
                AutosaveObservation(pendingRevision = 3L, publishedRevision = 2L, gate = GATE, flushes = 0L),
                START + CAP,
                POLICY,
            )

        assertEquals(QUIET, publishedWithNewerCapture.dueNanos(START + CAP))
        assertEquals(START + CAP + CAP, publishedWithNewerCapture.capDeadlineNanos)

        val settled =
            publishedWithNewerCapture.observed(
                AutosaveObservation(pendingRevision = null, publishedRevision = 3L, gate = GATE, flushes = 0L),
                START + CAP,
                POLICY,
            )
        assertNull(settled.dueNanos(START + CAP))
    }

    private fun idle(): AutosaveSchedule = AutosaveSchedule.idle()

    private fun capture(
        revision: Long?,
        gate: Any? = GATE,
        flushes: Long = 0L,
    ): AutosaveObservation =
        AutosaveObservation(
            pendingRevision = revision,
            publishedRevision = null,
            gate = gate,
            flushes = flushes,
        )

    private companion object {
        val POLICY: AutosavePolicy = AutosavePolicy.DEFAULT
        val QUIET: Long = POLICY.quietNanos
        val CAP: Long = POLICY.latencyCapNanos
        val HALF_QUIET: Long = POLICY.quietNanos / 2L
        const val START: Long = 1_000_000_000_000L
        const val GATE: String = "idle"
    }
}
