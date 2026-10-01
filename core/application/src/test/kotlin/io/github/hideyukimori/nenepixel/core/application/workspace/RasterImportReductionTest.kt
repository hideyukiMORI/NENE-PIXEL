package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.reduced
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.rejected
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.unchanged
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.PendingRasterImport
import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

/** ADR 0033: the pending import is set and cleared only through workspace actions and never rejects. */
internal class RasterImportReductionTest {
    private val reducer = WorkspaceReducer.create()
    private val initial = WorkspaceState.create(canvas(2, 1))
    private val admission = CommandGateway.create(state(canvas(2, 1))).captureSource()
    private val pending = pending()

    @Test
    fun `the workspace starts without a pending import`() {
        assertNull(initial.pendingImport)
    }

    @Test
    fun `set stores the pending import and leaves the rest of the workspace untouched`() {
        val set = reduced(reduce(initial, WorkspaceAction.SetPendingRasterImport(pending)))

        assertSame(pending, set.pendingImport)
        assertEquals(initial.withPendingImport(pending), set)
        assertSame(initial.viewport, set.viewport)
        assertSame(initial.editTarget, set.editTarget)
        assertNull(set.underlay)
        assertNull(set.paletteEditSession)
    }

    @Test
    fun `setting the same instance again is unchanged`() {
        val set = reduced(reduce(initial, WorkspaceAction.SetPendingRasterImport(pending)))

        val same = unchanged(reduce(set, WorkspaceAction.SetPendingRasterImport(pending)))

        assertEquals(WorkspaceNoChangeReason.PendingRasterImportAlreadySet, same.reason)
        assertSame(set, same.nextState)
    }

    @Test
    fun `another instance replaces the pending import`() {
        val set = reduced(reduce(initial, WorkspaceAction.SetPendingRasterImport(pending)))
        val other = pending()

        val replaced = reduced(reduce(set, WorkspaceAction.SetPendingRasterImport(other)))

        assertSame(other, replaced.pendingImport)
    }

    @Test
    fun `clear removes the pending import and clearing nothing is unchanged`() {
        val set = reduced(reduce(initial, WorkspaceAction.SetPendingRasterImport(pending)))

        val cleared = reduced(reduce(set, WorkspaceAction.ClearPendingRasterImport))
        assertNull(cleared.pendingImport)
        assertEquals(initial, cleared)

        val nothing = unchanged(reduce(initial, WorkspaceAction.ClearPendingRasterImport))
        assertEquals(WorkspaceNoChangeReason.NoPendingRasterImport, nothing.reason)
        assertSame(initial, nothing.nextState)
    }

    @Test
    fun `set and clear keep the gesture preview`() {
        val drawing = reduced(reduce(initial, WorkspaceAction.BeginGesturePreview(canvas(2, 1), position(1, 0))))
        assertNotNull(drawing.preview)

        val set = reduced(reduce(drawing, WorkspaceAction.SetPendingRasterImport(pending)))
        assertSame(drawing.preview, set.preview)
        val cleared = reduced(reduce(set, WorkspaceAction.ClearPendingRasterImport))
        assertSame(drawing.preview, cleared.preview)
    }

    @Test
    fun `during a palette session clear passes and set is rejected`() {
        assertTrue(WorkspaceAction.ClearPendingRasterImport.isAllowedDuringPaletteSession())
        assertFalse(WorkspaceAction.SetPendingRasterImport(pending).isAllowedDuringPaletteSession())

        val runtime = EditorRuntime.create(canvas(2, 1), defaultDefinition) { defaultDocumentId }
        reduced(runtime.reduce(WorkspaceAction.SetPendingRasterImport(pending)))
        reduced(runtime.paletteOperations.beginPaletteEdit())

        val refused = rejected(runtime.reduce(WorkspaceAction.SetPendingRasterImport(pending())))
        assertEquals(WorkspaceActionRejection.PaletteSessionActive, refused.rejection)
        assertSame(pending, runtime.state.workspaceState.pendingImport)

        reduced(runtime.reduce(WorkspaceAction.ClearPendingRasterImport))
        assertNull(runtime.state.workspaceState.pendingImport)
        assertNotNull(runtime.state.workspaceState.paletteEditSession)
    }

    private fun reduce(
        state: WorkspaceState,
        action: WorkspaceAction,
    ): WorkspaceReductionResult = reducer.reduce(state, action, admission)

    private fun pending(): PendingRasterImport =
        PendingRasterImport.planned(raster(RED_PIXEL, BLACK_PIXEL), canvas(2, 1), defaultDefinition)

    private fun raster(vararg packed: Int): ImportRaster =
        when (val result = ImportRaster.create(packed.size, 1, packed)) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Raster rejected: ${result.rejection}")
        }

    private companion object {
        const val BLACK_PIXEL: Int = 0x000000ff
        const val RED_PIXEL: Int = 0xff0000ff.toInt()
    }
}
