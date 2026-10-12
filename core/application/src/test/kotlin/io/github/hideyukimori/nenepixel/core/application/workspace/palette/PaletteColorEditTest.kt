package io.github.hideyukimori.nenepixel.core.application.workspace.palette

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPosition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.blackIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.definition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.greenIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeSourceToken
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class PaletteColorEditTest {
    private val base = RuntimeSourceToken(1L, defaultDocumentId, HistoryPosition.initial)

    @Test
    fun `many samples retain only the start and finish as one undoable entry`() {
        val start = PaletteEditSession.begin(base, defaultDefinition)
        val active = changed(start.beginColorEdit(redIndex))
        val preview =
            (0..255).fold(active) { session, channel ->
                changed(session.updateColorEdit(color(channel, 3, 7, 0)))
            }

        assertTrue(preview.isColorEditActive)
        assertSame(start.draft, preview.colorEdit?.start)
        assertSame(start.timeline, preview.timeline)
        assertEquals(start.cursor, preview.cursor)
        val finished = changed(preview.finishColorEdit())
        assertFalse(finished.isColorEditActive)
        assertEquals(1, finished.timeline.size)
        assertSame(start.draft, finished.timeline.single().before)
        assertEquals(preview.draft, finished.timeline.single().after)
        assertEquals(defaultDefinition, changed(finished.undo()).draft)
        assertEquals(finished.draft, changed(changed(finished.undo()).redo()).draft)
    }

    @Test
    fun `returning to starting exact RGBA preserves the redo branch`() {
        val original = PaletteEditSession.begin(base, defaultDefinition)
        val edited = changed(original.edit(PaletteDraftOperation.SetSlotColor(redIndex, green)))
        val start = changed(edited.undo())
        val away = changed(changed(start.beginColorEdit(redIndex)).updateColorEdit(color(8, 9, 10, 0)))
        val returned = changed(away.updateColorEdit(red))
        val finished = changed(returned.finishColorEdit())

        assertSame(start.draft, finished.draft)
        assertSame(start.timeline, finished.timeline)
        assertEquals(start.cursor, finished.cursor)
        assertTrue(finished.canRedoDraft)
        assertEquals(edited.draft, changed(finished.redo()).draft)
    }

    @Test
    fun `same color sample and unsampled finish create no history`() {
        val start = PaletteEditSession.begin(base, defaultDefinition)
        val active = changed(start.beginColorEdit(redIndex))

        assertSame(PaletteDraftTransition.Unchanged, active.updateColorEdit(red))
        assertEquals(start, changed(active.finishColorEdit()))
        assertTrue(start.timeline.isEmpty())
    }

    @Test
    fun `hidden RGB change at zero alpha is an exact draft history change`() {
        val startDefinition = definition(blackIndex, black, color(1, 2, 3, 0))
        val start = PaletteEditSession.begin(base, startDefinition)
        val finalColor = color(3, 2, 1, 0)
        val preview = changed(changed(start.beginColorEdit(redIndex)).updateColorEdit(finalColor))

        val finished = changed(preview.finishColorEdit())

        assertEquals(
            finalColor,
            finished.draft.palette
                .entries()[redIndex.value]
                .color,
        )
        assertEquals(1, finished.timeline.size)
        assertEquals(startDefinition, changed(finished.undo()).draft)
        assertFalse(finished.isIdentity())
    }

    @Test
    fun `cancellation restores exact definition and timeline with redo available`() {
        val original = PaletteEditSession.begin(base, defaultDefinition)
        val edited = changed(original.edit(PaletteDraftOperation.SetSlotColor(redIndex, green)))
        val start = changed(edited.undo())
        val preview = changed(changed(start.beginColorEdit(redIndex)).updateColorEdit(black))

        val cancelled = changed(preview.cancelColorEdit())

        assertEquals(start, cancelled)
        assertSame(start.draft, cancelled.draft)
        assertSame(start.timeline, cancelled.timeline)
        assertEquals(start.composedRemap(), cancelled.composedRemap())
        assertEquals(edited.draft, changed(cancelled.redo()).draft)
    }

    @Test
    fun `preview after reorder and removal preserves the pregesture source mapping`() {
        val start = PaletteEditSession.begin(base, defaultDefinition)
        val reordered = changed(start.edit(PaletteDraftOperation.Reorder(listOf(greenIndex, blackIndex, redIndex))))
        val removed = changed(reordered.edit(PaletteDraftOperation.RemoveSlot(blackIndex, redIndex)))
        val preview = changed(changed(removed.beginColorEdit(blackIndex)).updateColorEdit(color(12, 34, 56, 78)))

        val remap = preview.composedRemap()

        assertSame(start.origin, remap.source)
        assertSame(preview.draft, remap.target)
        assertEquals(removed.composedRemap().destinations(), remap.destinations())
        assertEquals(listOf(blackIndex, redIndex, blackIndex), remap.destinations())
        assertSame(removed.timeline, preview.timeline)
        val finished = changed(preview.finishColorEdit())
        assertEquals(remap, finished.composedRemap())
        assertEquals(removed.draft, changed(finished.undo()).draft)
    }

    @Test
    fun `finishing at the history limit uses ordinary oldest first retention`() {
        val start =
            (0 until PixelLimits.MAX_HISTORY_ENTRIES).fold(PaletteEditSession.begin(base, defaultDefinition)) {
                session,
                step,
                ->
                changed(session.edit(PaletteDraftOperation.SetSlotColor(redIndex, color(step, 1, 2, 3))))
            }
        val preview = changed(changed(start.beginColorEdit(redIndex)).updateColorEdit(green))

        val finished = changed(preview.finishColorEdit())

        assertEquals(PixelLimits.MAX_HISTORY_ENTRIES, finished.timeline.size)
        assertEquals(PixelLimits.MAX_HISTORY_ENTRIES, finished.cursor)
        assertSame(start.timeline[1].before, finished.timeline.first().before)
        assertSame(start.draft, finished.timeline.last().before)
        assertEquals(start.composedRemap().destinations(), finished.composedRemap().destinations())
    }

    @Test
    fun `invalid begin absent active edit and pending import return typed rejection`() {
        val start = PaletteEditSession.begin(base, defaultDefinition)
        assertEquals(
            WorkspaceActionRejection.PaletteIndexOutsidePalette(paletteIndex(3), 3),
            rejected(start.beginColorEdit(paletteIndex(3))),
        )
        listOf(start.updateColorEdit(black), start.finishColorEdit(), start.cancelColorEdit()).forEach { transition ->
            assertEquals(draftRejected(PaletteDraftRejection.NoColorEdit), rejected(transition))
        }
        val pending = changed(start.stageImport(defaultDefinition))
        assertEquals(draftRejected(PaletteDraftRejection.ImportPending), rejected(pending.beginColorEdit(redIndex)))
    }

    private fun changed(transition: PaletteDraftTransition): PaletteEditSession =
        assertInstanceOf(PaletteDraftTransition.Changed::class.java, transition).session

    private fun rejected(transition: PaletteDraftTransition): WorkspaceActionRejection =
        assertInstanceOf(PaletteDraftTransition.Rejected::class.java, transition).rejection

    private fun draftRejected(reason: PaletteDraftRejection): WorkspaceActionRejection =
        WorkspaceActionRejection.PaletteDraftRejected(reason)

    private fun color(
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ): PixelColor = PixelColor.create(channel(red), channel(green), channel(blue), channel(alpha))

    private fun channel(value: Int): ColorChannel =
        when (val result = ColorChannel.create(value)) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Invalid fixture channel: ${result.rejection}")
        }
}
