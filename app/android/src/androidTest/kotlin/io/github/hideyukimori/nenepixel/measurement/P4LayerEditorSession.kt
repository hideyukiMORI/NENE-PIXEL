package io.github.hideyukimori.nenepixel.measurement

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import io.github.hideyukimori.nenepixel.EditorRuntimeViewModel
import io.github.hideyukimori.nenepixel.MainActivity
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntimeState
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveStateToken
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryStatus
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportMappingResult
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTransform
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal class P4LayerEditorSession(
    private val compose: ComposeTestRule,
    private val scenario: ActivityScenario<MainActivity>,
) {
    lateinit var model: EditorRuntimeViewModel
        private set
    val documents = P4LayerFixtureDocuments(scenario, ::await)

    fun retrieveActualModel() {
        scenario.onActivity { activity ->
            model =
                ViewModelProvider(
                    activity,
                    EditorRuntimeViewModel.factory(activity.application),
                )[EditorRuntimeViewModel::class.java]
        }
        awaitQuiescent()
    }

    fun newEmptyDocument() {
        click("editor_file")
        click("editor_new_document")
        compose.onNodeWithTag("editor_document_width").performTextReplacement("256")
        compose.onNodeWithTag("editor_document_height").performTextReplacement("256")
        click("editor_create")
        awaitCanvas()
        selectBlackPencil()
        awaitQuiescent()
        withState(P4LayerEditorStateChecks::empty)
    }

    fun loadMaximum(name: String) {
        click("editor_file")
        click("editor_load")
        documents.openInProductionPicker(name)
        awaitQuiescent()
        check(model.persistenceOperations.value.lastOutcome == PersistenceLastOutcome.Loaded)
        awaitCanvas()
        selectBlackPencil()
        withState {
            P4LayerEditorStateChecks.maximum(it, committed = false)
            check(it.workspaceState.preview == null)
        }
    }

    fun holdLongPreview() {
        compose.onNodeWithTag(CANVAS).performTouchInput {
            down(point(0))
            advanceEventTime(100)
            repeat(16) { move -> moveTo(point(if (move % 2 == 0) 255 else 0), delayMillis = 20) }
            advanceEventTime(100)
        }
        compose.waitForIdle()
        withState {
            P4LayerEditorStateChecks.maximum(it, committed = false)
            P4LayerEditorStateChecks.preview(it)
        }
    }

    fun commit(): AutosaveStateToken {
        compose.onNodeWithTag(CANVAS).performTouchInput { up() }
        compose.waitForIdle()
        val capture = model.autosaveStates.value
        val token =
            checkNotNull(capture.pendingStateToken ?: capture.publishingStateToken ?: capture.publishedStateToken)
        awaitQuiescent(token)
        assertCommitted()
        return token
    }

    fun undoRedoCycles(published: AutosaveStateToken) {
        repeat(10) {
            click("editor_undo")
            withState { state ->
                check(
                    state.documentState.revision.value == 0L &&
                        state.historyAvailability == HistoryAvailability.RedoAvailable,
                )
            }
            click("editor_redo")
            withState { state ->
                check(
                    state.documentState.revision.value == 1L &&
                        state.historyAvailability == HistoryAvailability.UndoAvailable,
                )
            }
        }
        awaitQuiescent(published)
        assertCommitted()
    }

    fun awaitQuiescent(published: AutosaveStateToken? = null) {
        await {
            val operation = model.persistenceOperations.value
            check(operation.lastOutcome !is PersistenceLastOutcome.Failed)
            check(
                operation.recoveryStatus !is RecoveryStatus.Unknown &&
                    operation.recoveryStatus != RecoveryStatus.UnadoptedCandidate,
            )
            val autosave = model.autosaveStates.value
            check(
                autosave.lastOutcome == AutosaveLastOutcome.None ||
                    autosave.lastOutcome is AutosaveLastOutcome.Published ||
                    autosave.lastOutcome == AutosaveLastOutcome.Deferred,
            )
            operation.phase == PersistenceOperationPhase.Idle && operation.recoveryStatus == RecoveryStatus.Clear &&
                autosave.pendingStateToken == null && !autosave.publishing &&
                (published == null || autosave.publishedStateToken == published)
        }
        compose.waitForIdle()
    }

    private fun assertCommitted() =
        withState {
            P4LayerEditorStateChecks.maximum(it, committed = true)
            check(it.workspaceState.preview == null)
        }

    private fun selectBlackPencil() {
        click("editor_pencil_tool")
        click("editor_open_palette")
        click("editor_palette_entry_1")
        compose.waitForIdle()
    }

    private fun click(tag: String) = compose.onNodeWithTag(tag).assertIsEnabled().performClick()

    private fun awaitCanvas() = await { compose.onAllNodes(hasTestTag(CANVAS)).fetchSemanticsNodes().size == 1 }

    private fun await(condition: () -> Boolean) = compose.waitUntil(15_000, condition)

    private fun withState(block: (EditorRuntimeState) -> Unit) {
        compose.waitForIdle()
        scenario.onActivity { block(model.runtime.state) }
    }

    private fun TouchInjectionScope.point(cell: Int): Offset {
        val current = model.controller.renderState
        val surface =
            (
                ViewportSurface.create(
                    width,
                    height,
                    compose.density.density.toDouble(),
                ) as ViewportValueResult.Created
            ).value
        val transform =
            (
                ViewportTransform.create(
                    current.document.size,
                    surface,
                    current.viewport,
                ) as ViewportValueResult.Created
            ).value
        val position =
            PixelPosition.create(
                (PixelX.create(cell) as DomainValueResult.Created).value,
                (PixelY.create(cell) as DomainValueResult.Created).value,
            )
        val bounds = (transform.toSurfaceBounds(position) as ViewportMappingResult.Mapped).value
        return Offset(((bounds.left + bounds.right) / 2).toFloat(), ((bounds.top + bounds.bottom) / 2).toFloat())
    }

    private companion object {
        const val CANVAS: String = "editor_canvas_256_256"
    }
}
