package io.github.hideyukimori.nenepixel

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveStateToken
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryStatus
import io.github.hideyukimori.nenepixel.presentation.compose.R
import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorControls
import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorOperationAvailability
import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorPersistenceControls
import io.github.hideyukimori.nenepixel.presentation.compose.editor.NenePixelEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/** Actual enabled controls and app admission, without MainActivity, provider or ordinary storage (#244). */
internal class EditorOperationAdmissionTest {
    @get:Rule
    val rule = createAndroidComposeRule(ComponentActivity::class.java)

    @Test
    fun enabledSaveAsIsNotSilentlyLostWhileExportWaitsForAutosave() {
        val fixture = EditorOperationAdmissionFixture()
        try {
            show(fixture)
            val captured = drawAndHoldPublication(fixture)
            submitRetainedExport(fixture)
            verifyCompetingControls(fixture)
            completeFirstOperation(fixture, captured)
            assertEquals(0, fixture.saveCalls)
            verifySavePositiveControl(fixture, 0)
        } finally {
            releaseAndClear(fixture)
        }
    }

    @Test
    fun cancellationKeepsAdmissionBusyThroughCleanupThenRestoresControls() {
        val fixture = EditorOperationAdmissionFixture()
        try {
            show(fixture)
            val captured = drawAndHoldPublication(fixture)
            submitRetainedExport(fixture)
            awaitExportAfterPublication(fixture, captured)
            rule.onNodeWithTag("editor_file").performClick()
            rule.onNodeWithTag("editor_cancel_operation").assertIsEnabled().performClick()
            rule.waitUntil(OBSERVATION_BUDGET_MILLIS) { fixture.cleanupEntered }
            assertEquals(EditorOperationAvailability.Busy, fixture.model.operationAvailability.value)
            rule.onNodeWithTag("editor_save_as").assertIsNotEnabled()
            assertEquals(rule.activity.getString(R.string.cancelling_operation), visibleStatus())
            fixture.report("cancel-cleanup-held", visibleStatus())
            fixture.releaseCleanup()
            awaitIdle(fixture)
            rule.onNodeWithTag("editor_save_as").assertIsEnabled()
            rule.onNodeWithTag("editor_close_panel").performClick()
            verifySavePositiveControl(fixture, 0)
        } finally {
            releaseAndClear(fixture)
        }
    }

    private fun show(fixture: EditorOperationAdmissionFixture) {
        rule.activityRule.scenario.onActivity { activity -> fixture.attach(activity) }
        val model = fixture.model
        val editor = EditorControls(model.controller.renderStates, model.controller.callbacks)
        val storage =
            EditorPersistenceControls(
                model.persistenceOperations,
                model.autosaveStates,
                model.persistenceCallbacks,
                model.operationAvailability,
            )
        rule.setContent { NenePixelEditor(editor, storage, fixture.settings) }
        rule.waitUntil(OBSERVATION_BUDGET_MILLIS) {
            model.persistenceOperations.value.let {
                it.phase == PersistenceOperationPhase.Idle && it.recoveryStatus == RecoveryStatus.Clear
            }
        }
    }

    private fun drawAndHoldPublication(fixture: EditorOperationAdmissionFixture): AutosaveStateToken {
        rule.onNodeWithTag("editor_canvas_16_16").performTouchInput {
            down(percentOffset(CANVAS_CENTER, CANVAS_CENTER))
            up()
        }
        rule.waitUntil(OBSERVATION_BUDGET_MILLIS) { fixture.model.autosaveStates.value.pendingStateToken != null }
        val token = requireNotNull(fixture.model.autosaveStates.value.pendingStateToken)
        rule.runOnIdle { fixture.model.flushAutosave() }
        rule.waitUntil(OBSERVATION_BUDGET_MILLIS) { fixture.publicationEntered }
        assertEquals(token, fixture.model.autosaveStates.value.publishingStateToken)
        assertEquals(PersistenceOperationPhase.Idle, fixture.model.persistenceOperations.value.phase)
        assertEquals(fixture.model.runtime.state.documentState, fixture.publishedDocument)
        fixture.report("publication-held", visibleStatus())
        return token
    }

    private fun submitRetainedExport(fixture: EditorOperationAdmissionFixture) {
        rule.onNodeWithTag("editor_file").performClick()
        rule.onNodeWithTag("editor_export_png").assertIsEnabled().performClick()
        rule.onNodeWithTag("editor_export_png").assertDoesNotExist()
        assertEquals(0, fixture.exportCalls)
        assertEquals(EditorOperationAvailability.Busy, fixture.model.operationAvailability.value)
        assertEquals(rule.activity.getString(R.string.preparing_operation), visibleStatus())
        fixture.report("first-export-waiting", visibleStatus())
    }

    private fun verifyCompetingControls(fixture: EditorOperationAdmissionFixture) {
        rule.onNodeWithTag("editor_file").performClick()
        listOf("save_as", "load", "new_document", "export_png", "import_png").forEach { action ->
            rule.onNodeWithTag("editor_$action").assertIsNotEnabled()
        }
        assertEquals(0, fixture.saveCalls)
        assertEquals(0, fixture.exportCalls)
        fixture.report("competing-file-disabled", visibleStatus())
        rule.onNodeWithTag("editor_close_panel").performClick()
        rule.onNodeWithTag("editor_open_palette").performClick()
        rule.onNodeWithTag("editor_import_palette_json").assertIsNotEnabled()
        rule.onNodeWithTag("editor_close_panel").performClick()
        verifyUnderlayControls(fixture)
    }

    private fun verifyUnderlayControls(fixture: EditorOperationAdmissionFixture) {
        rule.onNodeWithTag("editor_layer_chip").performClick()
        rule.onNodeWithTag("editor_underlay_pick").assertIsNotEnabled()
        rule.runOnIdle { fixture.installUnderlay() }
        rule.onNodeWithTag("editor_underlay_more").performClick()
        rule.onNodeWithTag("editor_underlay_replace").assertIsNotEnabled()
        rule.onNodeWithTag("editor_underlay_fit").assertIsEnabled().performClick()
        rule.onNodeWithTag("editor_layer_panel_close").performClick()
        fixture.report("palette-underlay-pick-replace-disabled-local-fit-enabled", visibleStatus())
    }

    private fun completeFirstOperation(
        fixture: EditorOperationAdmissionFixture,
        token: AutosaveStateToken,
    ) {
        awaitExportAfterPublication(fixture, token)
        fixture.releaseExport()
        awaitIdle(fixture)
        assertEquals(PersistenceLastOutcome.Cancelled, fixture.model.persistenceOperations.value.lastOutcome)
        fixture.report("first-export-complete", visibleStatus())
    }

    private fun awaitExportAfterPublication(
        fixture: EditorOperationAdmissionFixture,
        token: AutosaveStateToken,
    ) {
        fixture.releasePublication()
        rule.waitUntil(OBSERVATION_BUDGET_MILLIS) { fixture.exportEntered }
        assertEquals(token, fixture.model.autosaveStates.value.publishedStateToken)
        assertEquals(1, fixture.exportCalls)
        assertEquals(fixture.publishedDocument, fixture.exportedDocument)
        fixture.report("publication-complete-export-entered", visibleStatus())
    }

    private fun verifySavePositiveControl(
        fixture: EditorOperationAdmissionFixture,
        savesBeforeControl: Int,
    ) {
        rule.onNodeWithTag("editor_file").performClick()
        rule.onNodeWithTag("editor_save_as").assertIsEnabled().performClick()
        rule.waitUntil(OBSERVATION_BUDGET_MILLIS) { fixture.saveCalls == savesBeforeControl + 1 }
        awaitIdle(fixture)
        assertNotNull(fixture.savedDocument)
        assertEquals(fixture.model.runtime.state.documentState, fixture.savedDocument)
        fixture.report("save-positive-control-complete", visibleStatus())
    }

    private fun awaitIdle(fixture: EditorOperationAdmissionFixture) {
        rule.waitUntil(OBSERVATION_BUDGET_MILLIS) {
            fixture.model.persistenceOperations.value.phase == PersistenceOperationPhase.Idle &&
                !fixture.model.autosaveStates.value.publishing &&
                fixture.model.operationAvailability.value == EditorOperationAvailability.Available
        }
        // A main-thread barrier also drains the completing worker's finally/completion callbacks.
        rule.runOnIdle { }
    }

    private fun visibleStatus(): String =
        rule
            .onNodeWithTag("editor_operation_status")
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString { it.text }

    private fun releaseAndClear(fixture: EditorOperationAdmissionFixture) {
        fixture.releaseAll()
        if (fixture.attached) {
            try {
                awaitIdle(fixture)
            } finally {
                rule.activityRule.scenario.onActivity { activity -> activity.viewModelStore.clear() }
            }
        }
    }

    private companion object {
        const val OBSERVATION_BUDGET_MILLIS: Long = 10_000L
        const val CANVAS_CENTER: Float = 0.5f
    }
}
