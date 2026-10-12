package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.id
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.required
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectSaveOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.initializedFixture
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.PendingRasterImport
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackTestValues.MS
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackTestValues.document
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackTestValues.pulse
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackTestValues.runtime
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackTestValues.start
import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** Only new playback/transport combinations; existing storage correctness suites remain unchanged. */
internal class PlaybackPersistenceTest {
    @Test fun `PNG captures editing frame and save captures whole document while display continues`() =
        runBlocking {
            val fixture = initializedFixture()
            val runtime = fixture.runtime
            runtime.transact { transaction ->
                PersistenceTransition(
                    transaction.coordination,
                    Unit,
                    RuntimeOwnerEffect.ReplaceOwners(RuntimeOwners.create(document())),
                )
            }
            val captured = runtime.state.documentState
            val token = start(runtime)
            pulse(runtime, token, 0L)
            pulse(runtime, token, 10 * MS)
            assertEquals(id(2), runtime.state.workspaceState.playback.view.frameId)
            val exported = assertInstanceOf(PngExportStart.Started::class.java, runtime.pngExportOperations.begin())
            assertSame(captured, exported.capture.document)
            assertSame(captured.frames.first(), exported.capture.frame)
            assertEquals(id(4), exported.capture.frameId)
            assertSame(token, runtime.state.workspaceState.playback.view.token)
            runtime.pngExportOperations.complete(exported.handle, PngExportOutcome.Exported)
            val gate = CompletableDeferred<Unit>()
            fixture.storage.saveHandler = {
                gate.await()
                ProjectSaveOutcome.Saved
            }
            val saving = async(start = CoroutineStart.UNDISPATCHED) { fixture.workflow.saveAs() }
            pulse(runtime, token, 40 * MS)
            gate.complete(Unit)
            assertInstanceOf(PersistenceRequestResult.Completed::class.java, saving.await())
            assertEquals(listOf(captured), fixture.storage.savedDocuments)
            assertSame(captured, runtime.state.documentState)
            assertEquals(id(4), runtime.state.workspaceState.activeFrameId)
            assertEquals(id(8), runtime.state.workspaceState.playback.view.frameId)
            assertSame(token, runtime.state.workspaceState.playback.view.token)
            assertEquals(DocumentDirtyState.Clean, runtime.state.dirtyState)
        }

    @Test fun `pick before playback keeps captured frame anchor and pending completion stops current display`() {
        val runtime = runtime()
        val original = runtime.state.documentState
        val pick = assertInstanceOf(PngImportPickStart.Started::class.java, runtime.pngImportOperations.beginPick())
        val token = start(runtime)
        pulse(runtime, token, 0L)
        pulse(runtime, token, 10 * MS)
        assertEquals(id(2), runtime.state.workspaceState.playback.view.frameId)
        val raster = required(ImportRaster.create(1, 1, intArrayOf(0xFF0000FF.toInt())))
        val pending = PendingRasterImport.planned(raster, pick.target)
        assertInstanceOf(
            PersistenceRequestResult.Completed::class.java,
            runtime.pngImportOperations.completePick(pick.handle, PngImportOutcome.Picked(raster), pending),
        )
        assertNull(runtime.state.workspaceState.playback.view.token)
        assertNull(runtime.state.workspaceState.playback.view.frameId)
        assertSame(pending, runtime.state.workspaceState.pendingImport)
        assertSame(pick.target, pending.target)
        assertEquals(id(4), pending.target.frameId)
        assertEquals(layerId(1), pending.target.anchorLayerId)
        assertEquals(id(4), runtime.state.workspaceState.activeFrameId)
        assertSame(original, runtime.state.documentState)
        assertEquals(0L, runtime.read { it.documentState().revision.value })
        assertNull(runtime.autosaveProjection.value.pendingStateToken)
    }
}
