package io.github.hideyukimori.nenepixel.core.application.render

import io.github.hideyukimori.nenepixel.core.application.document.command.AddLayerCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.DocumentCommand
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.definition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.editor.PaletteApplyResult
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftOperation
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.fail

/**
 * The shared Issue #148 fixture: a painted two-layer runtime and the one check that a draft's picture equals the
 * picture the real Apply leaves in the document.
 */
internal object PaletteDraftCompositeFixture {
    const val WIDTH: Int = 3
    const val HEIGHT: Int = 2
    val black: PixelColor = color(0, 0, 0, 255)
    val halfRed: PixelColor = color(255, 0, 0, 128)
    val green: PixelColor = color(0, 255, 0, 255)
    val quarterBlue: PixelColor = color(0, 0, 255, 64)
    val opaqueBlue: PixelColor = color(0, 0, 255, 255)
    val clear: PixelColor = color(255, 255, 255, 0)
    val sourceDefinition: PaletteDefinition = definition(paletteIndex(0), black, halfRed, green, quarterBlue)

    /**
     * Two layers on a 3x2 canvas: cells 4 and 5 stay Empty in both, cell 2 is bottom-only, and translucent
     * colours overlap at cells 0 and 1.
     */
    fun drawnRuntime(source: PaletteDefinition = sourceDefinition): EditorRuntime {
        val runtime = EditorRuntime.create(canvas(WIDTH, HEIGHT), source, FixedDocumentIdSource())
        paint(runtime, LayerId.first(), listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0))
        execute(runtime, AddLayerCommand.create(runtime.captureSource(), LayerId.first()))
        val layers = runtime.state.documentState.layers
        paint(runtime, layers.last().id, listOf(0 to 3, 1 to 1, 3 to 2))
        return runtime
    }

    /** Runs [command] and requires it to be applied. */
    fun execute(
        runtime: EditorRuntime,
        command: DocumentCommand,
    ) {
        assertInstanceOf(CommandResult.Applied::class.java, runtime.execute(command))
    }

    fun begin(runtime: EditorRuntime) {
        assertInstanceOf(WorkspaceReductionResult.Reduced::class.java, runtime.paletteOperations.beginPaletteEdit())
    }

    fun reduce(
        runtime: EditorRuntime,
        action: WorkspaceAction,
    ) {
        assertInstanceOf(WorkspaceReductionResult.Reduced::class.java, runtime.reduce(action))
    }

    fun edit(operation: PaletteDraftOperation): WorkspaceAction = WorkspaceAction.EditPaletteDraft(operation)

    fun draftPixels(runtime: EditorRuntime): IntArray {
        val session = runtime.state.workspaceState.paletteEditSession ?: fail("Palette session was closed")
        val result = PaletteDraftComposite.render(runtime.state.documentState, session)
        return assertInstanceOf(PaletteDraftCompositeResult.Rendered::class.java, result).image.copyPackedRgba8888()
    }

    fun documentPixels(runtime: EditorRuntime): IntArray =
        DocumentComposite.render(runtime.state.documentState).copyPackedRgba8888()

    /**
     * Begins a session on [runtime], reduces [actions], then applies for real: the draft picture must equal the
     * applied document's picture, and differ from the original exactly when [changes]. Returns the draft picture.
     */
    fun assertDraftEqualsApply(
        runtime: EditorRuntime,
        actions: List<WorkspaceAction>,
        changes: Boolean,
    ): IntArray {
        val original = documentPixels(runtime)
        begin(runtime)
        actions.forEach { action -> reduce(runtime, action) }
        val draft = draftPixels(runtime)

        assertSame(PaletteApplyResult.Applied, runtime.paletteOperations.applyPaletteDraft())

        assertArrayEquals(documentPixels(runtime), draft)
        assertEquals(changes, !original.contentEquals(draft))
        return draft
    }

    private fun paint(
        runtime: EditorRuntime,
        layerId: LayerId,
        cells: List<Pair<Int, Int>>,
    ) {
        cells.forEach { (cell, index) ->
            val size = runtime.state.documentState.size
            val stroke = stroke(size, listOf(position(cell % WIDTH, cell / WIDTH)), paletteIndex(index))
            execute(runtime, ApplyStrokeCommand.create(runtime.captureSource(), layerId, stroke))
        }
    }
}

private fun color(
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

private fun <T> DomainValueResult<T>.created(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> fail("Test value was rejected: $rejection")
    }

private class FixedDocumentIdSource : DocumentIdSource {
    override fun nextDocumentId(): DocumentId =
        when (val result = DocumentId.create("2".repeat(DOCUMENT_ID_LENGTH))) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Document ID fixture was rejected: ${result.rejection}")
        }
}

private const val DOCUMENT_ID_LENGTH: Int = 32
