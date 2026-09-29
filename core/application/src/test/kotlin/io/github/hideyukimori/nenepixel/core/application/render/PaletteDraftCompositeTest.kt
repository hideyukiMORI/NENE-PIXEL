package io.github.hideyukimori.nenepixel.core.application.render

import io.github.hideyukimori.nenepixel.core.application.document.command.AddLayerCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPosition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.definition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.editor.PaletteApplyResult
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeSourceToken
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftOperation
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteEditSession
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
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

        assertEquals(2, runtime.state.documentState.layers.size)
        val pixels = documentPixels(runtime)
        assertEquals(0, pixels[EMPTY_CELL])
        assertFalse(pixels.all { it and ALPHA_MASK == ALPHA_MASK || it == 0 })
    }

    private fun assertDraftEqualsApply(
        vararg actions: WorkspaceAction,
        changes: Boolean,
    ) {
        val runtime = drawnRuntime()
        val original = documentPixels(runtime)
        begin(runtime)
        actions.forEach { action -> reduce(runtime, action) }
        val draft = draftPixels(runtime)

        assertSame(PaletteApplyResult.Applied, runtime.paletteOperations.applyPaletteDraft())

        assertArrayEquals(documentPixels(runtime), draft)
        assertEquals(changes, !original.contentEquals(draft))
    }

    private fun draftPixels(runtime: EditorRuntime): IntArray {
        val session = runtime.state.workspaceState.paletteEditSession ?: fail("Palette session was closed")
        val result = PaletteDraftComposite.render(runtime.state.documentState, session)
        return assertInstanceOf(PaletteDraftCompositeResult.Rendered::class.java, result).image.copyPackedRgba8888()
    }

    private fun documentPixels(runtime: EditorRuntime): IntArray =
        DocumentComposite.render(runtime.state.documentState).copyPackedRgba8888()

    /**
     * Two layers on a 3x2 canvas: cells 4 and 5 stay Empty in both, cell 2 is bottom-only, and translucent
     * colours overlap at cells 0 and 1.
     */
    private fun drawnRuntime(): EditorRuntime {
        val runtime = EditorRuntime.create(canvas(WIDTH, HEIGHT), sourceDefinition, FixedDocumentIdSource())
        paint(runtime, LayerId.first(), listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0))
        applied(runtime.execute(AddLayerCommand.create(runtime.captureSource(), LayerId.first())))
        val layers = runtime.state.documentState.layers
        paint(runtime, layers.last().id, listOf(0 to 3, 1 to 1, 3 to 2))
        return runtime
    }

    private fun paint(
        runtime: EditorRuntime,
        layerId: LayerId,
        cells: List<Pair<Int, Int>>,
    ) {
        cells.forEach { (cell, index) ->
            val size = runtime.state.documentState.size
            val stroke = stroke(size, listOf(cellPosition(cell)), paletteIndex(index))
            applied(runtime.execute(ApplyStrokeCommand.create(runtime.captureSource(), layerId, stroke)))
        }
    }

    private fun cellPosition(cell: Int): PixelPosition = position(cell % WIDTH, cell / WIDTH)

    private fun applied(result: CommandResult) {
        assertInstanceOf(CommandResult.Applied::class.java, result)
    }

    private fun begin(runtime: EditorRuntime) {
        assertInstanceOf(WorkspaceReductionResult.Reduced::class.java, runtime.paletteOperations.beginPaletteEdit())
    }

    private fun reduce(
        runtime: EditorRuntime,
        action: WorkspaceAction,
    ) {
        assertInstanceOf(WorkspaceReductionResult.Reduced::class.java, runtime.reduce(action))
    }

    private fun edit(operation: PaletteDraftOperation): WorkspaceAction = WorkspaceAction.EditPaletteDraft(operation)

    private companion object {
        const val WIDTH: Int = 3
        const val HEIGHT: Int = 2
        const val EMPTY_CELL: Int = 4
        const val ALPHA_MASK: Int = 0xff
        val black: PixelColor = color(0, 0, 0, 255)
        val halfRed: PixelColor = color(255, 0, 0, 128)
        val green: PixelColor = color(0, 255, 0, 255)
        val quarterBlue: PixelColor = color(0, 0, 255, 64)
        val opaqueBlue: PixelColor = color(0, 0, 255, 255)
        val sourceDefinition = definition(paletteIndex(0), black, halfRed, green, quarterBlue)

        fun color(
            red: Int,
            green: Int,
            blue: Int,
            alpha: Int,
        ): PixelColor =
            PixelColor.create(
                red = ColorChannel.create(red).created(),
                green = ColorChannel.create(green).created(),
                blue = ColorChannel.create(blue).created(),
                alpha = ColorChannel.create(alpha).created(),
            )

        fun <T> DomainValueResult<T>.created(): T =
            when (this) {
                is DomainValueResult.Created -> value
                is DomainValueResult.Rejected -> fail("Test value was rejected: $rejection")
            }
    }
}

private class FixedDocumentIdSource : DocumentIdSource {
    override fun nextDocumentId(): DocumentId =
        when (val result = DocumentId.create("2".repeat(DOCUMENT_ID_LENGTH))) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Document ID fixture was rejected: ${result.rejection}")
        }
}

private const val DOCUMENT_ID_LENGTH: Int = 32
