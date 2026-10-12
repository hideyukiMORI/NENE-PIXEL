package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.document.command.SetFrameDurationCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.UndoCommand
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.duration
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.frame
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.id
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.required
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.runtime
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layer
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveStateToken
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectSaveOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.initializedFixture
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.LayerImportMode
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.PendingRasterImport
import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class FrameImportCaptureTest {
    @Test fun `pending layer form keeps pick-begin frame anchor and source across focus changes`() {
        val original = state(frame(1, listOf(layer(1, "a"), layer(2, "b"))), frame(2, listOf(layer(9, "c"))))
        val runtime = runtime(original)
        val start = assertInstanceOf(PngImportPickStart.Started::class.java, runtime.pngImportOperations.beginPick())
        runtime.reduce(WorkspaceAction.SelectFrame(id(2)))
        val raster = raster()
        val pending = PendingRasterImport.planned(raster, start.target)
        runtime.pngImportOperations.completePick(start.handle, PngImportOutcome.Picked(raster), pending)
        assertEquals(id(1), pending.target.frameId)
        assertEquals(layerId(2), pending.target.anchorLayerId)
        assertEquals(1, pending.target.frameOrdinal)
        assertEquals(2, pending.target.frameCount)
        assertSame(original, runtime.state.documentState)
        assertSame(
            RasterImportApplyResult.Applied,
            runtime.rasterImportOperations.applyLayerImport(LayerImportMode.Appending),
        )
        assertEquals(id(1), runtime.state.workspaceState.activeFrameId)
        assertEquals(layerId(10), runtime.state.workspaceState.activeLayerId)
        assertSame(original.frames[1], runtime.state.documentState.frames[1])
        assertNull(runtime.state.workspaceState.pendingImport)
        assertEquals(
            runtime.read {
                AutosaveStateToken(it.coordination.runtimeGeneration, it.historyPosition())
            },
            runtime.autosaveProjection.value.pendingStateToken,
        )
    }

    @Test fun `history-stale layer form is atomic and Undo to exact source readmits the same pending target`() {
        val runtime = runtime(state(frame(1, listOf(layer(1, "a"))), frame(2, listOf(layer(9, "b")))))
        val start = assertInstanceOf(PngImportPickStart.Started::class.java, runtime.pngImportOperations.beginPick())
        val raster = raster()
        val pending = PendingRasterImport.planned(raster, start.target)
        assertInstanceOf(
            CommandResult.Applied::class.java,
            runtime.execute(SetFrameDurationCommand.create(runtime.captureSource(), id(1), duration(250))),
        )
        runtime.pngImportOperations.completePick(start.handle, PngImportOutcome.Picked(raster), pending)
        val before = runtime.state
        assertEquals(
            RejectionReason.SourceHistoryMismatch,
            assertInstanceOf(
                RasterImportApplyResult.CommandRejected::class.java,
                runtime.rasterImportOperations.applyLayerImport(LayerImportMode.Converting),
            ).reason,
        )
        assertEquals(before, runtime.state)
        assertSame(pending, runtime.state.workspaceState.pendingImport)
        runtime.execute(UndoCommand.create(before.documentState.id, before.documentState.revision))
        assertSame(
            RasterImportApplyResult.Applied,
            runtime.rasterImportOperations.applyLayerImport(LayerImportMode.Converting),
        )
    }

    @Test fun `new-work plan remains independent of picked-from history and enters current-context switch`() {
        val runtime = runtime(state(frame(1, listOf(layer(1, "a")))))
        val start = assertInstanceOf(PngImportPickStart.Started::class.java, runtime.pngImportOperations.beginPick())
        val raster = raster()
        val pending = PendingRasterImport.planned(raster, start.target)
        runtime.execute(SetFrameDurationCommand.create(runtime.captureSource(), id(1), duration(250)))
        runtime.pngImportOperations.completePick(start.handle, PngImportOutcome.Picked(raster), pending)
        assertInstanceOf(SwitchStart.Confirmation::class.java, runtime.switchOperations.beginImportedWork())
        assertNull(runtime.state.workspaceState.pendingImport)
    }

    @Test fun `PNG export visibility and immutable capture use only the frame chosen at begin`() {
        val original =
            state(frame(1, listOf(layer(1, "a", visibility = LayerVisibility.Hidden))), frame(2, listOf(layer(9, "b"))))
        val runtime = runtime(original)
        assertSame(PngExportStart.NoVisibleLayer, runtime.pngExportOperations.begin())
        assertNull(runtime.read { it.coordination.activeOperation })
        runtime.reduce(WorkspaceAction.SelectFrame(id(2)))
        val start = assertInstanceOf(PngExportStart.Started::class.java, runtime.pngExportOperations.begin())
        runtime.reduce(WorkspaceAction.SelectFrame(id(1)))
        assertSame(original, start.capture.document)
        assertSame(original.frames[1], start.capture.frame)
        assertEquals(id(2), start.capture.frameId)
        runtime.pngExportOperations.complete(start.handle, PngExportOutcome.Exported)
        assertEquals(id(1), runtime.state.workspaceState.activeFrameId)
        assertSame(original, runtime.state.documentState)
    }

    @Test fun `save captures all frames while later duration and focus survive completion`() =
        runBlocking {
            val fixture = initializedFixture()
            val runtime = fixture.runtime
            val original = state(frame(1, listOf(layer(1, "a"))), frame(2, listOf(layer(9, "b"))))
            runtime.transact { transaction ->
                PersistenceTransition(
                    transaction.coordination,
                    Unit,
                    RuntimeOwnerEffect.ReplaceOwners(RuntimeOwners.create(original)),
                )
            }
            val gate = CompletableDeferred<Unit>()
            fixture.storage.saveHandler = {
                gate.await()
                ProjectSaveOutcome.Saved
            }
            val saving = async(start = CoroutineStart.UNDISPATCHED) { fixture.workflow.saveAs() }
            runtime.reduce(WorkspaceAction.SelectFrame(id(2)))
            assertInstanceOf(
                CommandResult.Applied::class.java,
                runtime.execute(SetFrameDurationCommand.create(runtime.captureSource(), id(1), duration(250))),
            )
            val changed = runtime.state.documentState
            val token = runtime.autosaveProjection.value.pendingStateToken
            gate.complete(Unit)
            assertInstanceOf(PersistenceRequestResult.Completed::class.java, saving.await())
            assertEquals(listOf(original), fixture.storage.savedDocuments)
            assertSame(changed, runtime.state.documentState)
            assertEquals(DocumentDirtyState.Dirty, runtime.state.dirtyState)
            assertEquals(token, runtime.autosaveProjection.value.pendingStateToken)
            assertEquals(id(2), runtime.state.workspaceState.activeFrameId)
            assertInstanceOf(
                CommandResult.Applied::class.java,
                runtime.execute(UndoCommand.create(changed.id, changed.revision)),
            )
            assertEquals(original, runtime.state.documentState)
            assertEquals(DocumentDirtyState.Clean, runtime.state.dirtyState)
            assertEquals(id(2), runtime.state.workspaceState.activeFrameId)
        }

    private fun raster(): ImportRaster = required(ImportRaster.create(1, 1, intArrayOf(0xFF0000FF.toInt())))
}
