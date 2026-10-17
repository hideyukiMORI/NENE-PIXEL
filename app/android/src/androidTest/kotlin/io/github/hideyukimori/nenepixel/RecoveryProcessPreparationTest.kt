package io.github.hideyukimori.nenepixel

import android.os.SystemClock
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout

/** Each method intentionally waits for the admitted host kill; no kill is a failing preparation. */
internal class RecoveryProcessPreparationTest {
    private val compose = createEmptyComposeRule()
    private var writerGate: RecoveryCancellationGate? = null

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(Timeout.seconds(60)).around(compose)

    @Test
    fun beforePublication() = prepare(RecoveryProcessCase.BeforePublication)

    @Test
    fun duringPhysicalPublication() = prepare(RecoveryProcessCase.DuringPublication)

    @Test
    fun afterVerifiedCompletion() = prepare(RecoveryProcessCase.VerifiedCompletion)

    private fun prepare(selected: RecoveryProcessCase): Unit =
        runBlocking {
            val admission = RecoveryProcessAdmission()
            assertEquals(selected, admission.case)
            val fixture = RecoveryProcessFixture(admission)
            fixture.prepare()
            fixture.application.registerActivityLifecycleCallbacks(fixture.callbacks)
            try {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity(fixture::assertActivityModel)
                    compose.onNodeWithTag("editor_canvas_2_1").assertExists()
                    val deadline = reachPhase(fixture, scenario)
                    scenario.moveToState(Lifecycle.State.CREATED)
                    fixture.callbacks.awaitStopped(deadline)
                    fixture.assertPublicationHadNotStartedBeforeStop()
                    assertPhase(fixture)
                    val journal = RecoveryProcessJournal(admission)
                    journal.ready(fixture, deadline)
                    awaitHostKill(journal, deadline)
                }
            } finally {
                fixture.application.unregisterActivityLifecycleCallbacks(fixture.callbacks)
                fixture.release()
            }
        }

    private suspend fun reachPhase(
        fixture: RecoveryProcessFixture,
        scenario: ActivityScenario<MainActivity>,
    ): Long =
        when (fixture.admission.case) {
            RecoveryProcessCase.BeforePublication -> {
                holdExport(fixture, scenario)
            }

            RecoveryProcessCase.DuringPublication -> {
                holdWriter(fixture, scenario)
            }

            RecoveryProcessCase.VerifiedCompletion -> {
                val deadline = SystemClock.elapsedRealtime() + RecoveryProcessAdmission.GATE_MILLIS
                scenario.onActivity { fixture.captureSecond() }
                deadline
            }
        }

    private suspend fun holdExport(
        fixture: RecoveryProcessFixture,
        scenario: ActivityScenario<MainActivity>,
    ): Long {
        compose.onNodeWithTag("editor_file").performClick()
        val deadline = SystemClock.elapsedRealtime() + RecoveryProcessAdmission.GATE_MILLIS
        compose.onNodeWithTag("editor_export_png").performClick()
        withTimeout(RecoveryProcessAdmission.GATE_MILLIS) {
            fixture.ports.export.entered
                .await()
        }
        scenario.onActivity { fixture.captureSecond() }
        return deadline
    }

    private fun holdWriter(
        fixture: RecoveryProcessFixture,
        scenario: ActivityScenario<MainActivity>,
    ): Long {
        writerGate = fixture.file.hold(cancelFromPort = false)
        val deadline = SystemClock.elapsedRealtime() + RecoveryProcessAdmission.GATE_MILLIS
        scenario.onActivity { fixture.captureSecond() }
        return deadline
    }

    private suspend fun assertPhase(fixture: RecoveryProcessFixture) {
        fixture.callbacks.requireStopped()
        when (fixture.admission.case) {
            RecoveryProcessCase.BeforePublication -> {
                fixture.assertBeforePublication()
            }

            RecoveryProcessCase.DuringPublication -> {
                requireNotNull(writerGate).awaitEntered()
                fixture.assertPartialWrite()
            }

            RecoveryProcessCase.VerifiedCompletion -> {
                fixture.awaitSecondPublished()
            }
        }
    }

    private suspend fun awaitHostKill(
        journal: RecoveryProcessJournal,
        deadline: Long,
    ): Nothing {
        val remaining = deadline - SystemClock.elapsedRealtime()
        try {
            check(remaining > 0L) { "Preparation missed the external kill window" }
            withTimeout(remaining) { awaitCancellation() }
        } finally {
            // This executes only if the process survived: a timeout/abort must never become a PASS.
            journal.expired()
        }
    }
}
