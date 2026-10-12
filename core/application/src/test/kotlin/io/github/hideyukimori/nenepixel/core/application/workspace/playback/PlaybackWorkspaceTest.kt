package io.github.hideyukimori.nenepixel.core.application.workspace.playback

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.id
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layer
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.editor.PersistenceTransition
import io.github.hideyukimori.nenepixel.core.application.editor.PngImportPickStart
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryFixtures
import io.github.hideyukimori.nenepixel.core.application.workspace.BeginPaletteEdit
import io.github.hideyukimori.nenepixel.core.application.workspace.BeginSelectionSession
import io.github.hideyukimori.nenepixel.core.application.workspace.EditTarget
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReducer
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.LayerImportTarget
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.PendingRasterImport
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackTestValues.runtime
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackTestValues.start
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.EyedropperState
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelectItem
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayInteraction
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.geometry.DocumentPoint
import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class PlaybackWorkspaceTest {
    @Test fun `playing raw draw selection and viewport actions reject before existing pointer admission`() {
        val runtime = runtime()
        start(runtime)
        val before = runtime.state.workspaceState
        listOf(
            WorkspaceAction.BeginGesturePreview(runtime.state.documentState.size, position(0, 0)),
            WorkspaceAction.ExtendGesturePreview(DocumentPoint.create(1, 0)),
            WorkspaceAction.PrepareGestureCommit,
            WorkspaceAction.SetViewport(before.viewport),
            WorkspaceAction.BeginSelectionPointer(position(0, 0)),
            WorkspaceAction.UpdateSelectionPointer(DocumentPoint.create(1, 0)),
            WorkspaceAction.FinishSelectionPointer,
        ).forEach { assertRefused(runtime, it, WorkspaceActionRejection.PlaybackRejected(PlaybackRejection.Playing)) }
        runtime.reduce(WorkspaceAction.SelectFrame(id(4)))
        assertSame(before, runtime.state.workspaceState)
        assertRefused(runtime, WorkspaceAction.SelectFrame(id(99)), WorkspaceActionRejection.FrameNotFound(id(99)))
        runtime.reduce(WorkspaceAction.SelectTool(DrawingTool.Eraser))
        assertNull(runtime.state.workspaceState.playback.view.token)
    }

    @Test fun `start refuses single frame palette and pending import atomically`() {
        val single = FrameTestValues.runtime(FrameTestValues.state(FrameTestValues.frame(1, listOf(layer(1, "")))))
        assertRefused(
            single,
            WorkspaceAction.StartPlayback,
            WorkspaceActionRejection.PlaybackRejected(PlaybackRejection.InsufficientFrames),
        )
        val runtime = runtime()
        val old = start(runtime)
        runtime.paletteOperations.beginPaletteEdit()
        assertInstanceOf(
            WorkspaceReductionResult.Unchanged::class.java,
            runtime.reduce(WorkspaceAction.StopPlaybackIfCurrent(old)),
        )
        assertRefused(runtime, WorkspaceAction.StartPlayback, WorkspaceActionRejection.PaletteSessionActive)
        assertInstanceOf(WorkspaceReductionResult.Unchanged::class.java, runtime.reduce(WorkspaceAction.StopPlayback))
        runtime.reduce(WorkspaceAction.CancelPaletteEdit)
        val pick = assertInstanceOf(PngImportPickStart.Started::class.java, runtime.pngImportOperations.beginPick())
        val pending =
            PendingRasterImport.planned(
                FrameTestValues.required(ImportRaster.create(1, 1, intArrayOf(0))),
                pick.target,
            )
        runtime.reduce(WorkspaceAction.SetPendingRasterImport(pending))
        assertRefused(
            runtime,
            WorkspaceAction.StartPlayback,
            WorkspaceActionRejection.PlaybackRejected(PlaybackRejection.PendingImport),
        )
    }

    @Test fun `invalid editing focus is refused without fallback or workspace mutation`() {
        val runtime = runtime()
        val workspace =
            runtime.state.workspaceState.withEditTarget(
                EditTarget.create(id(99), layerId(1), runtime.state.workspaceState.activePaletteIndex),
            )
        val invalid =
            assertInstanceOf(
                WorkspaceReductionResult.Rejected::class.java,
                WorkspaceReducer.create().reduce(workspace, WorkspaceAction.StartPlayback, runtime.captureSource()),
            )
        assertEquals(WorkspaceActionRejection.FrameNotFound(id(99)), invalid.rejection)
        assertSame(workspace, invalid.nextState)
    }

    @Test fun `start cancels unfinished stroke and selection pointer through canonical reductions`() {
        val runtime = runtime()
        runtime.reduce(WorkspaceAction.BeginGesturePreview(runtime.state.documentState.size, position(0, 0)))
        start(runtime)
        assertNull(runtime.state.workspaceState.preview)
        runtime.reduce(WorkspaceAction.StopPlayback)
        runtime.selectionOperations.beginSelection(layerId(1))
        runtime.reduce(WorkspaceAction.BeginSelectionPointer(position(0, 0)))
        start(runtime)
        assertNull(runtime.state.workspaceState.selectionSession)
        assertNull(runtime.state.workspaceState.preview)
    }

    @Test fun `start closes armed quick select and rests adjustment while retaining underlay value`() {
        val runtime = runtime()
        runtime.reduce(WorkspaceAction.OpenQuickSelect)
        runtime.reduce(WorkspaceAction.HighlightQuickSelectItem(QuickSelectItem.Eyedropper))
        runtime.reduce(WorkspaceAction.ConfirmQuickSelect)
        val underlay =
            ReferenceUnderlay
                .placed(
                    UnderlayMemoryFixtures.image(),
                    runtime.state.documentState.size,
                ).adjusting()
        runtime.reduce(WorkspaceAction.SetReferenceUnderlay(underlay))
        val memory = runtime.underlayMemory.value
        start(runtime)
        assertEquals(EyedropperState.Idle, runtime.state.workspaceState.quickSelection.eyedropper)
        assertNull(runtime.state.workspaceState.quickSelection.menu)
        assertEquals(
            UnderlayInteraction.Resting,
            runtime.state.workspaceState.underlay
                ?.interaction,
        )
        assertSame(
            underlay.image,
            runtime.state.workspaceState.underlay
                ?.image,
        )
        assertSame(memory, runtime.underlayMemory.value)
    }

    @Test fun `internal palette selection and pending reductions cancel through the same common policy`() {
        val actions: List<(EditorRuntime.RuntimeTransaction) -> WorkspaceAction> =
            listOf(
                { BeginPaletteEdit(it.switchContext().source, it.documentState().definition) },
                { BeginSelectionSession(it.captureSource()) },
                { transaction ->
                    val target =
                        LayerImportTarget.capture(
                            transaction.captureSource(),
                            transaction.workspaceState().activeFrameId,
                            transaction.workspaceState().activeLayerId,
                        )
                    WorkspaceAction.SetPendingRasterImport(
                        PendingRasterImport.planned(
                            FrameTestValues.required(ImportRaster.create(1, 1, intArrayOf(0))),
                            target,
                        ),
                    )
                },
                { WorkspaceAction.OpenQuickSelect },
                {
                    WorkspaceAction.SetReferenceUnderlay(
                        ReferenceUnderlay.placed(UnderlayMemoryFixtures.image(), it.documentState().size).adjusting(),
                    )
                },
                { WorkspaceAction.SelectFrame(id(2)) },
            )
        actions.forEach { action ->
            val runtime = runtime()
            val token = start(runtime)
            runtime.transact { transaction ->
                val result = transaction.reduceWorkspace(action(transaction))
                assertInstanceOf(WorkspaceReductionResult.Reduced::class.java, result)
                PersistenceTransition(transaction.coordination, Unit)
            }
            assertNull(runtime.state.workspaceState.playback.view.token)
            assertSame(PlaybackPulseResult.Stale, runtime.advancePlayback(token, 0L))
        }
    }

    private fun assertRefused(
        runtime: EditorRuntime,
        action: WorkspaceAction,
        reason: WorkspaceActionRejection,
    ) {
        val before = runtime.state
        val autosave = runtime.autosaveProjection.value
        val result = assertInstanceOf(WorkspaceReductionResult.Rejected::class.java, runtime.reduce(action))
        assertEquals(reason, result.rejection)
        assertSame(before.workspaceState, result.nextState)
        assertEquals(before, runtime.state)
        assertSame(autosave, runtime.autosaveProjection.value)
    }
}
