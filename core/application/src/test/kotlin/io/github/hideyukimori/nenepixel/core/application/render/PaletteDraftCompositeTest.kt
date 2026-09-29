package io.github.hideyukimori.nenepixel.core.application.render

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPosition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.definition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeSourceToken
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.HEIGHT
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.WIDTH
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.begin
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.black
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.documentPixels
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.draftPixels
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.drawnRuntime
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.edit
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.green
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.halfRed
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.opaqueBlue
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.quarterBlue
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.reduce
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.sourceDefinition
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftOperation
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteEditSession
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class PaletteDraftCompositeTest {
    @Test
    fun `slot color change shows the applied picture`() {
        assertDraftEqualsApply(edit(PaletteDraftOperation.SetSlotColor(paletteIndex(1), opaqueBlue)), changes = true)
    }

    @Test
    fun `appended slot shows the applied picture`() {
        assertDraftEqualsApply(edit(PaletteDraftOperation.AppendSlot(opaqueBlue)), changes = false)
    }

    @Test
    fun `removing a used slot shows the applied picture`() {
        assertDraftEqualsApply(
            edit(PaletteDraftOperation.RemoveSlot(paletteIndex(1), paletteIndex(3))),
            changes = true,
        )
    }

    @Test
    fun `reorder shows the applied picture`() {
        val order = listOf(3, 1, 0, 2).map(::paletteIndex)
        assertDraftEqualsApply(edit(PaletteDraftOperation.Reorder(order)), changes = false)
    }

    @Test
    fun `confirmed import shows the applied picture`() {
        assertDraftEqualsApply(
            WorkspaceAction.ImportPaletteDraft(definition(paletteIndex(0), opaqueBlue, halfRed, green)),
            WorkspaceAction.AssignPaletteImportSlot(paletteIndex(3), paletteIndex(1)),
            WorkspaceAction.ConfirmPaletteImport,
            changes = true,
        )
    }

    @Test
    fun `a chain of draft edits shows the applied picture`() {
        assertDraftEqualsApply(
            edit(PaletteDraftOperation.SetSlotColor(paletteIndex(2), opaqueBlue)),
            edit(PaletteDraftOperation.AppendSlot(green)),
            edit(PaletteDraftOperation.Reorder(listOf(4, 0, 3, 2, 1).map(::paletteIndex))),
            edit(PaletteDraftOperation.RemoveSlot(paletteIndex(1), paletteIndex(0))),
            WorkspaceAction.ImportPaletteDraft(definition(paletteIndex(1), halfRed, black, quarterBlue, green)),
            WorkspaceAction.ConfirmPaletteImport,
            edit(PaletteDraftOperation.SetSlotColor(paletteIndex(0), green)),
            changes = true,
        )
    }

    @Test
    fun `draft undo shows the previous draft picture`() {
        val runtime = drawnRuntime()
        begin(runtime)
        reduce(runtime, edit(PaletteDraftOperation.SetSlotColor(paletteIndex(1), opaqueBlue)))
        val previous = draftPixels(runtime)
        reduce(runtime, edit(PaletteDraftOperation.RemoveSlot(paletteIndex(3), paletteIndex(0))))
        assertFalse(previous.contentEquals(draftPixels(runtime)))

        reduce(runtime, WorkspaceAction.UndoPaletteDraft)

        assertArrayEquals(previous, draftPixels(runtime))
    }

    @Test
    fun `an unchanged session shows the document composite`() {
        val runtime = drawnRuntime()
        begin(runtime)

        assertArrayEquals(documentPixels(runtime), draftPixels(runtime))
    }

    @Test
    fun `a document palette other than the session origin is a source mismatch`() {
        val runtime = drawnRuntime()
        val other = definition(paletteIndex(0), black, halfRed, green)
        val base = RuntimeSourceToken(1L, defaultDocumentId, HistoryPosition.initial)
        val session = PaletteEditSession.begin(base, other)

        assertSame(
            PaletteDraftCompositeResult.SourceMismatch,
            PaletteDraftComposite.render(runtime.state.documentState, session),
        )
    }

    @Test
    fun `a pending import keeps the draft picture`() {
        val runtime = drawnRuntime()
        begin(runtime)
        reduce(runtime, edit(PaletteDraftOperation.SetSlotColor(paletteIndex(1), opaqueBlue)))
        val draft = draftPixels(runtime)

        reduce(runtime, WorkspaceAction.ImportPaletteDraft(definition(paletteIndex(0), green, green, green, green)))

        val session = runtime.state.workspaceState.paletteEditSession
        assertNotNull(session?.pendingImport)
        assertArrayEquals(draft, draftPixels(runtime))
    }

    @Test
    fun `the fixture has two layers empty cells and translucent overlaps`() {
        val runtime = drawnRuntime()
        val layers = runtime.state.documentState.layers
        val cells = 0 until WIDTH * HEIGHT
        val coveredBy = cells.map { cell -> layers.count { cellOf(it, cell) is PixelCell.Covered } }
        val overlaps = cells.filter { coveredBy[it] == layers.size }
        val pixels = documentPixels(runtime)
        val translucentTops = overlaps.filter { alphaOf(cellOf(layers.last(), it)) in 1 until OPAQUE }

        assertEquals(2, layers.size)
        assertEquals(listOf(4, 5), cells.filter { coveredBy[it] == 0 })
        assertEquals(listOf(4, 5), cells.filter { pixels[it] == 0 })
        assertEquals(listOf(0, 1, 3), overlaps)
        assertEquals(listOf(0, 1), translucentTops)
        assertEquals(listOf(0, 1), translucentTops.filter { pixels[it] != colorOf(cellOf(layers.last(), it)) })
        assertEquals(listOf(0), overlaps.filter { pixels[it] and OPAQUE in 1 until OPAQUE })
    }

    private fun assertDraftEqualsApply(
        vararg actions: WorkspaceAction,
        changes: Boolean,
    ) {
        PaletteDraftCompositeFixture.assertDraftEqualsApply(drawnRuntime(), actions.toList(), changes)
    }

    private fun cellOf(
        layer: Layer,
        cell: Int,
    ): PixelCell =
        when (val result = layer.snapshot.cellAt(position(cell % WIDTH, cell / WIDTH))) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Fixture cell was rejected: ${result.rejection}")
        }

    /** The packed RGBA8888 of a covered cell's source palette colour. */
    private fun colorOf(cell: PixelCell): Int {
        val covered = cell as? PixelCell.Covered ?: fail("Fixture cell is Empty")
        val entry = sourceDefinition.palette.entries()[covered.index.value]
        return entry.color.toPackedRgba8888()
    }

    private fun alphaOf(cell: PixelCell): Int = colorOf(cell) and OPAQUE

    private companion object {
        const val OPAQUE: Int = 0xff
    }
}
