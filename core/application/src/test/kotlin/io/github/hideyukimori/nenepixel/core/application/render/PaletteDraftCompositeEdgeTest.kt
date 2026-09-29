package io.github.hideyukimori.nenepixel.core.application.render

import io.github.hideyukimori.nenepixel.core.application.document.command.SetLayerVisibilityCommand
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.definition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.editor.PaletteApplyResult
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.assertDraftEqualsApply
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.begin
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.clear
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.documentPixels
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.draftPixels
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.drawnRuntime
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.edit
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.execute
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.green
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.opaqueBlue
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.reduce
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.sourceDefinition
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftOperation
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteLimits
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

/**
 * Issue #148 P2t: the draft picture equals the picture the real Apply leaves in the document for the cases the
 * shared fixture does not reach on its own (hidden layer, alpha 0, a full 256-entry palette, evicted draft history).
 */
internal class PaletteDraftCompositeEdgeTest {
    @Test
    fun `a hidden layer is remapped like a visible one`() {
        val actions =
            listOf(
                edit(PaletteDraftOperation.Reorder(listOf(3, 1, 0, 2).map(::paletteIndex))),
                edit(PaletteDraftOperation.RemoveSlot(paletteIndex(1), paletteIndex(3))),
                edit(PaletteDraftOperation.SetSlotColor(paletteIndex(0), opaqueBlue)),
            )
        val hidden = drawnRuntime()
        val layers = hidden.state.documentState.layers
        val topLayer = layers.last().id
        execute(hidden, SetLayerVisibilityCommand.create(hidden.captureSource(), topLayer, LayerVisibility.Hidden))
        val hiddenDraft = assertDraftEqualsApply(hidden, actions, changes = true)
        val visibleDraft = assertDraftEqualsApply(drawnRuntime(), actions, changes = true)

        execute(hidden, SetLayerVisibilityCommand.create(hidden.captureSource(), topLayer, LayerVisibility.Visible))

        assertFalse(hiddenDraft.contentEquals(visibleDraft), "The hidden layer must matter to the picture")
        assertArrayEquals(visibleDraft, documentPixels(hidden))
    }

    @Test
    fun `an alpha 0 slot shows the applied picture`() {
        val draft =
            assertDraftEqualsApply(
                drawnRuntime(),
                listOf(edit(PaletteDraftOperation.SetSlotColor(paletteIndex(1), clear))),
                changes = true,
            )

        assertEquals(green.toPackedRgba8888(), draft[CLEAR_OVER_GREEN_CELL])
    }

    @Test
    fun `a full 256 entry palette shows the applied picture up to index 255`() {
        val runtime = drawnRuntime(fullDefinition())
        val last = PaletteLimits.MAX_ENTRY_COUNT - 1
        val actions =
            listOf(
                edit(PaletteDraftOperation.Reorder((last downTo 0).map(::paletteIndex))),
                edit(PaletteDraftOperation.SetSlotColor(paletteIndex(last - 1), opaqueBlue)),
            )

        assertDraftEqualsApply(runtime, actions, changes = true)

        val layers = runtime.state.documentState.layers
        assertEquals(last, layers.maxOf { it.snapshot.maximumIndex.value })
    }

    @Test
    fun `an evicted draft history shows the applied picture`() {
        val runtime = drawnRuntime()
        val original = documentPixels(runtime)
        begin(runtime)
        reduce(runtime, edit(PaletteDraftOperation.SetSlotColor(paletteIndex(1), opaqueBlue)))
        repeat(PixelLimits.MAX_HISTORY_ENTRIES + 1) {
            reduce(runtime, edit(PaletteDraftOperation.Reorder(listOf(1, 2, 3, 0).map(::paletteIndex))))
        }
        val session = runtime.state.workspaceState.paletteEditSession ?: fail("Palette session was closed")
        assertEquals(PixelLimits.MAX_HISTORY_ENTRIES, session.timeline.size)
        assertNotSame(session.origin, session.evicted.target, "The recolour must have been evicted")
        val draft = draftPixels(runtime)

        assertSame(PaletteApplyResult.Applied, runtime.paletteOperations.applyPaletteDraft())

        assertArrayEquals(documentPixels(runtime), draft)
        assertFalse(original.contentEquals(draft))
    }

    private fun fullDefinition() =
        definition(
            paletteIndex(0),
            *(sourceDefinition.palette.entries().map { it.color } + (4 until PaletteLimits.MAX_ENTRY_COUNT).map(::gray))
                .toTypedArray(),
        )

    private fun gray(level: Int): PixelColor {
        val channel = channel(level)
        return PixelColor.create(channel, channel, channel, channel(OPAQUE))
    }

    private fun channel(value: Int): ColorChannel =
        when (val result = ColorChannel.create(value)) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Channel fixture was rejected: ${result.rejection}")
        }

    private companion object {
        const val OPAQUE: Int = 255
        const val CLEAR_OVER_GREEN_CELL: Int = 1
    }
}
