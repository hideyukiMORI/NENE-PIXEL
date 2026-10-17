package io.github.hideyukimori.nenepixel

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInitializationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryStatus
import io.github.hideyukimori.nenepixel.presentation.compose.R
import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorControls
import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorOperationAvailability
import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorPersistenceControls
import io.github.hideyukimori.nenepixel.presentation.compose.editor.NenePixelEditor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout

/** Real editor/VM/worker/scheduler/adapter, isolated synthetic files and no lifecycle exit (#247). */
internal class EditorRecoveryFaultTest {
    private val compose = createAndroidComposeRule(ComponentActivity::class.java)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(Timeout.seconds(90)).around(compose)

    @Test
    fun writeFailureIsVisibleAndExplicitFlushPublishesExactPendingCapture() {
        val fixture = EditorRecoveryFaultFixture("ui-write")
        try {
            show(fixture)
            compose.runOnIdle { fixture.captureAndFlush(RecoveryFaultPhase.WRITE) }
            awaitFailed(fixture)
            fixture.recovery.assertFailed()
            runBlocking { fixture.recovery.assertCandidate(fixture.recovery.first, 2L) }
            assertStatus(R.string.autosave_failed)
            record(fixture, "write-failed")
            fixture.recovery.file.fault = RecoveryFaultPhase.NONE
            val attempts = fixture.recovery.file.startCalls
            compose.runOnIdle { fixture.model.flushAutosave() }
            awaitPublished(fixture)
            runBlocking { fixture.recovery.assertPublished(fixture.recovery.second, 3L) }
            assertEquals(attempts + 1, fixture.recovery.file.startCalls)
            record(fixture, "flush-published-c2")
        } finally {
            close(fixture)
        }
    }

    @Test
    fun finishBeforeCommitIsUncertainAndFlushCannotBlindlyWrite() {
        val fixture = EditorRecoveryFaultFixture("ui-finish")
        try {
            show(fixture)
            compose.runOnIdle { fixture.captureAndFlush(RecoveryFaultPhase.FINISH_BEFORE_COMMIT) }
            awaitUncertain(fixture)
            fixture.recovery.assertUncertain(RecoveryRetirementFailure.FINISH)
            assertArrayEquals(
                fixture.recovery.firstBytes,
                fixture.recovery.file.baseFile
                    .readBytes(),
            )
            assertStatus(R.string.recovery_unavailable)
            // Preserve the uncommitted .new before any real AtomicFile.openRead can remove it.
            record(fixture, "finish-uncertain-with-new")
            fixture.recovery.file.fault = RecoveryFaultPhase.NONE
            val attempts = fixture.recovery.file.startCalls
            compose.runOnIdle { fixture.model.flushAutosave() }
            runBlocking { fixture.recovery.observeNoAdditionalWrite() }
            assertEquals(attempts, fixture.recovery.file.startCalls)
            assertStatus(R.string.recovery_unavailable)
            record(fixture, "flush-no-blind-write")
        } finally {
            close(fixture)
        }
    }

    @Test
    fun readBackUncertaintyOffersExactC2AndActualDeclineGateResumesPublication() {
        val fixture = EditorRecoveryFaultFixture("ui-read-back")
        try {
            show(fixture)
            compose.runOnIdle { fixture.captureAndFlush(RecoveryFaultPhase.READ_BACK_AFTER_COMMIT) }
            awaitUncertain(fixture)
            fixture.recovery.assertUncertain(RecoveryRetirementFailure.READ_BACK)
            assertStatus(R.string.recovery_unavailable)
            record(fixture, "read-back-uncertain")
            reinspectAndObserveOffer(fixture)
            compose.onNodeWithTag("editor_discard_unsaved").assertIsEnabled().performClick()
            awaitPublished(fixture)
            runBlocking {
                fixture.recovery.assertRetiredFinish(4, 4L)
                fixture.recovery.assertPublished(fixture.recovery.second, 5L)
            }
            assertEquals(EditorOperationAvailability.Available, fixture.model.operationAvailability.value)
            record(fixture, "decline-gate-published-c2")
        } finally {
            close(fixture)
        }
    }

    private fun show(fixture: EditorRecoveryFaultFixture) {
        runBlocking { fixture.recovery.prepare() }
        compose.activityRule.scenario.onActivity { fixture.attach(it) }
        val model = fixture.model
        val editor = EditorControls(model.controller.renderStates, model.controller.callbacks)
        val storage =
            EditorPersistenceControls(
                model.persistenceOperations,
                model.autosaveStates,
                model.persistenceCallbacks,
                model.operationAvailability,
            )
        compose.setContent { NenePixelEditor(editor, storage, fixture.settings) }
        compose.waitForIdle()
    }

    private fun reinspectAndObserveOffer(fixture: EditorRecoveryFaultFixture) {
        val recovery = fixture.recovery
        val uncertainBytes = recovery.file.baseFile.readBytes()
        assertFalse(recovery.firstBytes.contentEquals(uncertainBytes))
        recovery.file.fault = RecoveryFaultPhase.NONE
        runBlocking {
            recovery.assertCandidate(recovery.second, 3L)
            assertEquals(RecoveryInitializationResult.Ready, recovery.workflow.initializeRecovery())
        }
        compose.waitUntil(BUDGET_MILLIS) {
            fixture.model.persistenceOperations.value.recoveryStatus == RecoveryStatus.UnadoptedCandidate
        }
        compose.onNodeWithTag("editor_recovery_offer").assertExists()
        val starts = recovery.file.startCalls
        runBlocking { recovery.observeNoAdditionalWrite() }
        assertEquals(starts, recovery.file.startCalls)
        assertArrayEquals(uncertainBytes, recovery.file.baseFile.readBytes())
        record(fixture, "exact-c2-offer-no-write")
    }

    private fun awaitFailed(fixture: EditorRecoveryFaultFixture) {
        compose.waitUntil(BUDGET_MILLIS) {
            fixture.model.autosaveStates.value
                .let { !it.publishing && it.lastOutcome is AutosaveLastOutcome.Failed }
        }
    }

    private fun awaitUncertain(fixture: EditorRecoveryFaultFixture) {
        compose.waitUntil(BUDGET_MILLIS) {
            fixture.model.autosaveStates.value
                .let { !it.publishing && it.lastOutcome is AutosaveLastOutcome.Uncertain }
        }
    }

    private fun awaitPublished(fixture: EditorRecoveryFaultFixture) {
        compose.waitUntil(BUDGET_MILLIS) {
            fixture.model.autosaveStates.value.let {
                !it.publishing && it.pendingStateToken == null &&
                    it.publishedStateToken == fixture.recovery.second.token
            } && fixture.model.operationAvailability.value == EditorOperationAvailability.Available
        }
    }

    private fun assertStatus(resource: Int) {
        compose.onNodeWithTag("editor_operation_status").assertTextEquals(compose.activity.getString(resource))
    }

    private fun record(
        fixture: EditorRecoveryFaultFixture,
        stage: String,
    ) {
        val tag =
            if (fixture.model.persistenceOperations.value.recoveryStatus == RecoveryStatus.UnadoptedCandidate) {
                "editor_recovery_offer"
            } else {
                "editor_operation_status"
            }
        val visible =
            compose
                .onNodeWithTag(tag)
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .joinToString { it.text }
        fixture.recovery.record(stage, visible)
    }

    private fun close(fixture: EditorRecoveryFaultFixture) {
        try {
            if (fixture.attached) {
                try {
                    compose.waitUntil(BUDGET_MILLIS) {
                        !fixture.model.autosaveStates.value.publishing &&
                            fixture.model.persistenceOperations.value.phase == PersistenceOperationPhase.Idle &&
                            fixture.model.operationAvailability.value == EditorOperationAvailability.Available
                    }
                } finally {
                    compose.activityRule.scenario.onActivity { it.viewModelStore.clear() }
                }
            }
        } finally {
            fixture.recovery.close()
        }
    }

    private companion object {
        const val BUDGET_MILLIS: Long = 10_000L
    }
}
