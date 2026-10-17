package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInitializationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementFailure
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/** Native Android writer + actual app scheduler; the two cases use no ViewModel scheduler (#247). */
internal class AutosaveRecoveryFaultTest {
    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    @Test
    fun failedPublicationSuspendsWithoutSpinAndRealOperationGateResumesExactC2() =
        runBlocking {
            val fixture = RecoveryFaultFixture("scheduler-failed")
            var probe: AutosaveRecoveryFaultProbe? = null
            try {
                fixture.prepare()
                fixture.captureSecond(RecoveryFaultPhase.WRITE)
                val running = AutosaveRecoveryFaultProbe(fixture)
                probe = running
                awaitFailed(fixture)
                fixture.assertFailed()
                running.observeNoSpin("failed-no-spin")
                resumeThroughGate(fixture, running)
            } finally {
                try {
                    probe?.close()
                } finally {
                    fixture.close()
                }
            }
        }

    @Test
    fun uncertainPublicationDoesNotSpinOrBlindlyWriteBeforeCanonicalOfferResolution() =
        runBlocking {
            val fixture = RecoveryFaultFixture("scheduler-uncertain")
            var probe: AutosaveRecoveryFaultProbe? = null
            try {
                fixture.prepare()
                fixture.captureSecond(RecoveryFaultPhase.READ_BACK_AFTER_COMMIT)
                val running = AutosaveRecoveryFaultProbe(fixture)
                probe = running
                awaitUncertain(fixture)
                fixture.assertUncertain(RecoveryRetirementFailure.READ_BACK)
                running.observeNoSpin("uncertain-no-spin")
                fixture.file.fault = RecoveryFaultPhase.NONE
                val attempts = fixture.file.startCalls
                running.flushAndObserve()
                running.observeNoSpin("unavailable-no-spin-after-flush")
                assertEquals(attempts, fixture.file.startCalls)
                resolveOffer(fixture, running)
            } finally {
                try {
                    probe?.close()
                } finally {
                    fixture.close()
                }
            }
        }

    private suspend fun resumeThroughGate(
        fixture: RecoveryFaultFixture,
        probe: AutosaveRecoveryFaultProbe,
    ) {
        fixture.file.fault = RecoveryFaultPhase.NONE
        val attempts = fixture.file.startCalls
        val export = probe.launchExport()
        withTimeout(BUDGET_MILLIS) {
            fixture.ports.export.entered
                .await()
        }
        withTimeout(BUDGET_MILLIS) {
            fixture.workflow.autosave.first { it.lastOutcome == AutosaveLastOutcome.Deferred }
        }
        assertEquals(attempts, fixture.file.startCalls)
        probe.settle()
        fixture.ports.export.release()
        withTimeout(BUDGET_MILLIS) { export.await() }
        awaitPublished(fixture)
        fixture.assertPublished(fixture.second, 3L)
        assertEquals(attempts + 1, fixture.file.startCalls)
        fixture.record("operation-gate-published-c2")
    }

    private suspend fun resolveOffer(
        fixture: RecoveryFaultFixture,
        probe: AutosaveRecoveryFaultProbe,
    ) {
        val attempts = fixture.file.startCalls
        fixture.assertCandidate(fixture.second, 3L)
        assertEquals(RecoveryInitializationResult.Ready, fixture.workflow.initializeRecovery())
        probe.settle()
        withTimeout(BUDGET_MILLIS) {
            fixture.workflow.autosave.first { it.lastOutcome == AutosaveLastOutcome.OfferPending }
        }
        assertEquals(attempts, fixture.file.startCalls)
        fixture.record("scheduler-exact-c2-offer")
        probe.settle()
        fixture.workflow.declineRecovery()
        awaitPublished(fixture)
        fixture.assertRetiredFinish(4, 4L)
        fixture.assertPublished(fixture.second, 5L)
        assertEquals(attempts + 2, fixture.file.startCalls)
        fixture.record("scheduler-decline-gate-published-c2")
    }

    private suspend fun awaitFailed(fixture: RecoveryFaultFixture) {
        withTimeout(BUDGET_MILLIS) {
            fixture.workflow.autosave.first { !it.publishing && it.lastOutcome is AutosaveLastOutcome.Failed }
        }
    }

    private suspend fun awaitUncertain(fixture: RecoveryFaultFixture) {
        withTimeout(BUDGET_MILLIS) {
            fixture.workflow.autosave.first { !it.publishing && it.lastOutcome is AutosaveLastOutcome.Uncertain }
        }
    }

    private suspend fun awaitPublished(fixture: RecoveryFaultFixture) {
        withTimeout(BUDGET_MILLIS) {
            fixture.workflow.autosave.first { !it.publishing && it.publishedStateToken == fixture.second.token }
        }
    }

    private companion object {
        const val BUDGET_MILLIS: Long = 10_000L
    }
}
