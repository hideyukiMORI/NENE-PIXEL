package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.UndoCommand
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveStateToken
import io.github.hideyukimori.nenepixel.core.application.persistence.EditorPersistenceWorkflow
import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectLoadOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectSaveOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStoragePort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspection
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryPublicationOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRecordPort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
        val captured = idle().observed(capture(AutosaveStateTokens().next()), START, POLICY)

        assertEquals(QUIET, captured.dueNanos(START))
        assertEquals(1L, captured.dueNanos(START + QUIET - 1L))
        assertEquals(0L, captured.dueNanos(START + QUIET))
    }

    @Test
    fun newerCaptureRestartsTheQuietWindowWithoutMovingTheLatencyCap() {
        val tokens = AutosaveStateTokens()
        val first = idle().observed(capture(tokens.next()), START, POLICY)
        val second = first.observed(capture(tokens.next()), START + HALF_QUIET, POLICY)

        assertEquals(QUIET, second.dueNanos(START + HALF_QUIET))
        assertEquals(START + CAP, second.capDeadlineNanos)
    }

    @Test
    fun latencyCapBecomesDueWhileCapturesKeepRestartingTheQuietWindow() {
        var schedule = idle()
        val tokens = AutosaveStateTokens()
        var now = START
        while (now < START + CAP) {
            schedule = schedule.observed(capture(tokens.next()), now, POLICY)
            now += HALF_QUIET
        }

        assertEquals(0L, schedule.dueNanos(START + CAP))
    }

    @Test
    fun lifecycleFlushMakesAPendingCaptureDueImmediatelyAndIgnoresAnEmptyOne() {
        val stateToken = AutosaveStateTokens().next()
        val captured = idle().observed(capture(stateToken), START, POLICY)
        val flushed = captured.observed(capture(stateToken, flushes = 1L), START + HALF_QUIET, POLICY)

        assertEquals(0L, flushed.dueNanos(START + HALF_QUIET))
        assertNull(idle().observed(capture(stateToken = null, flushes = 1L), START, POLICY).dueNanos(START))
    }

    @Test
    fun deferredResultRetriesAfterOneQuietWindowInsteadOfSpinningOnAnElapsedCap() {
        val capped = idle().observed(capture(AutosaveStateTokens().next()), START, POLICY)
        val deferred = capped.deferred(START + CAP, POLICY)

        assertEquals(QUIET, deferred.dueNanos(START + CAP))
        assertEquals(0L, deferred.dueNanos(START + CAP + QUIET))
    }

    @Test
    fun offerPendingStopsRequestingUntilThePersistenceOperationGateChanges() {
        val tokens = AutosaveStateTokens()
        val suspended =
            idle()
                .observed(capture(tokens.next()), START, POLICY)
                .suspendedUntilGate()

        assertNull(suspended.dueNanos(START + CAP))

        val secondState = tokens.next()
        val sameGate = suspended.observed(capture(secondState), START + CAP, POLICY)
        assertNull(sameGate.dueNanos(START + CAP))

        val changedGate = sameGate.observed(capture(secondState, gate = "resolved"), START + CAP, POLICY)
        assertEquals(0L, changedGate.dueNanos(START + CAP))
    }

    @Test
    fun completedPublicationRestartsBothWindowsAndClearsWhenNothingIsPending() {
        val tokens = AutosaveStateTokens()
        val publishedState = tokens.next()
        val pendingState = tokens.next()
        val captured = idle().observed(capture(publishedState), START, POLICY)
        val publishedWithNewerCapture =
            captured.observed(
                capture(pendingState, published = publishedState),
                START + CAP,
                POLICY,
            )

        assertEquals(QUIET, publishedWithNewerCapture.dueNanos(START + CAP))
        assertEquals(START + CAP + CAP, publishedWithNewerCapture.capDeadlineNanos)

        val settled =
            publishedWithNewerCapture.observed(
                capture(null, published = pendingState),
                START + CAP,
                POLICY,
            )
        assertNull(settled.dueNanos(START + CAP))
    }

    @Test
    fun replacementStateAtTheSameRevisionRestartsTheQuietWindow() {
        val (firstBranch, replacementBranch) = AutosaveStateTokens().sameRevisionBranches()
        val first = idle().observed(capture(firstBranch), START, POLICY)

        val replacement = first.observed(capture(replacementBranch), START + HALF_QUIET, POLICY)

        assertEquals(QUIET, replacement.dueNanos(START + HALF_QUIET))
        assertEquals(START + CAP, replacement.capDeadlineNanos)
    }

    @Test
    fun publicationOfADifferentStateAtTheSameRevisionRestartsOnlyTheQuietWindow() {
        val tokens = AutosaveStateTokens()
        val (firstBranch, replacementBranch) = tokens.sameRevisionBranches()
        val pendingState = tokens.next()
        val first =
            idle().observed(
                capture(pendingState, published = firstBranch),
                START,
                POLICY,
            )

        val replacement =
            first.observed(
                capture(pendingState, published = replacementBranch),
                START + HALF_QUIET,
                POLICY,
            )

        assertEquals(QUIET, replacement.dueNanos(START + HALF_QUIET))
        assertEquals(START + CAP, replacement.capDeadlineNanos)
    }

    @Test
    fun captureDuringPublicationStartsOneCapThatLaterCoalescingKeeps() {
        val tokens = AutosaveStateTokens()
        val publishingState = tokens.next()
        val pendingState = tokens.next()
        val coalescedState = tokens.next()
        val initial = idle().observed(capture(publishingState), START, POLICY)
        val capturedDuringPublication =
            initial.observed(
                capture(pendingState, publishing = publishingState),
                START + HALF_QUIET,
                POLICY,
            )
        val coalescedDuringPublication =
            capturedDuringPublication.observed(
                capture(coalescedState, publishing = publishingState),
                START + QUIET,
                POLICY,
            )

        assertEquals(START + HALF_QUIET + CAP, capturedDuringPublication.capDeadlineNanos)
        assertEquals(capturedDuringPublication.capDeadlineNanos, coalescedDuringPublication.capDeadlineNanos)
    }

    @Test
    fun publicationCompletionKeepsTheCapOfACaptureObservedDuringPublication() {
        val tokens = AutosaveStateTokens()
        val publishingState = tokens.next()
        val pendingState = tokens.next()
        val publishing =
            idle().observed(
                capture(publishingState, publishing = publishingState),
                START,
                POLICY,
            )
        val capturedDuringPublication =
            publishing.observed(
                capture(pendingState, publishing = publishingState),
                START + HALF_QUIET,
                POLICY,
            )
        val completed =
            capturedDuringPublication.observed(
                capture(pendingState, published = publishingState),
                START + QUIET,
                POLICY,
            )

        assertEquals(capturedDuringPublication.capDeadlineNanos, completed.capDeadlineNanos)
        assertEquals(QUIET, completed.dueNanos(START + QUIET))
    }

    @Test
    fun conflatedPublicationCompletionDoesNotReuseThePublishedCaptureDeadline() {
        val tokens = AutosaveStateTokens()
        val publishedState = tokens.next()
        val pendingState = tokens.next()
        val beforePublication = idle().observed(capture(publishedState), START, POLICY)
        val afterPublication =
            beforePublication.observed(
                capture(pendingState, published = publishedState),
                START + HALF_QUIET,
                POLICY,
            )

        assertEquals(START + HALF_QUIET + CAP, afterPublication.capDeadlineNanos)
        assertEquals(QUIET, afterPublication.dueNanos(START + HALF_QUIET))
    }

    @Test
    fun aCaptureThatIsAlreadyPublishingDoesNotRequestAnotherPublication() {
        val stateToken = AutosaveStateTokens().next()
        val publishing =
            idle().observed(
                capture(stateToken, publishing = stateToken),
                START,
                POLICY,
            )

        assertNull(publishing.dueNanos(START + CAP))
        assertNull(publishing.quietDeadlineNanos)
        assertNull(publishing.capDeadlineNanos)
    }

    @Test
    fun aStaleResultCannotSuspendAStateObservedAfterItsRequestStarted() {
        val tokens = AutosaveStateTokens()
        val requested =
            idle()
                .observed(capture(tokens.next()), START, POLICY)
                .startedRequest()
        val requestedStateToken = requireNotNull(requested.observation.states.pending)
        val advanced =
            requested.observed(
                capture(tokens.next(), gate = "resolved"),
                START + HALF_QUIET,
                POLICY,
            )

        val afterStaleResult =
            advanced.applied(
                AutosaveRequestResult.Stale,
                requestedStateToken,
                START + HALF_QUIET,
                POLICY,
            )

        assertFalse(afterStaleResult.suspended)
        assertEquals(advanced, afterStaleResult)
    }

    private fun idle(): AutosaveSchedule = AutosaveSchedule.idle()

    private fun capture(
        stateToken: AutosaveStateToken?,
        published: AutosaveStateToken? = null,
        publishing: AutosaveStateToken? = null,
        gate: Any? = GATE,
        flushes: Long = 0L,
    ): AutosaveObservation =
        AutosaveObservation(
            states = AutosaveStateObservation(stateToken, published, publishing),
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

private class AutosaveStateTokens {
    private val runtime = createEditorRuntime()
    private val workflow =
        EditorPersistenceWorkflow.create(
            runtime,
            object : ProjectStoragePort {
                override suspend fun save(document: DocumentState): ProjectSaveOutcome = ProjectSaveOutcome.Cancelled

                override suspend fun load(): ProjectLoadOutcome = ProjectLoadOutcome.Cancelled
            },
            object : RecoveryRecordPort {
                override suspend fun inspect(): RecoveryInspection = RecoveryInspection.Missing

                override suspend fun retire(expected: ExpectedRecoveryLineage): RecoveryRetirementOutcome =
                    RecoveryRetirementOutcome.GenerationExhausted

                override suspend fun publishCandidate(
                    expected: ExpectedRecoveryLineage,
                    document: DocumentState,
                ): RecoveryPublicationOutcome = RecoveryPublicationOutcome.GenerationExhausted
            },
        )
    private var nextX: Int = 0

    fun next(): AutosaveStateToken {
        val current = runtime.state.documentState
        val position = PixelPosition.create(PixelX.create(nextX).required(), PixelY.create(0).required())
        val color =
            runtime.palette
                .entries()
                .first()
                .color
        val stroke = Stroke.create(current.size, listOf(position), StrokeEffect.Paint(color)).required()
        val result = runtime.execute(ApplyStrokeCommand.create(current.id, current.revision, stroke))
        check(result is CommandResult.Applied)
        nextX += 1
        return requireNotNull(workflow.autosave.value.pendingStateToken)
    }

    fun sameRevisionBranches(): Pair<AutosaveStateToken, AutosaveStateToken> {
        val first = next()
        val current = runtime.state.documentState
        check(runtime.execute(UndoCommand.create(current.id, current.revision)) is CommandResult.Applied)
        return first to next()
    }
}

private fun <T> DomainValueResult<T>.required(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Autosave schedule fixture was rejected: $rejection")
    }
