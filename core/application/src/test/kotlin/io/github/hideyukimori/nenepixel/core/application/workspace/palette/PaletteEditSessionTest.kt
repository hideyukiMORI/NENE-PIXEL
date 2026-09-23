package io.github.hideyukimori.nenepixel.core.application.workspace.palette

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPosition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.definition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.otherDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeSourceToken
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteLimits
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class PaletteEditSessionTest {
    private val definition = definition(paletteIndex(0), red, green)
    private val base = RuntimeSourceToken(1L, defaultDocumentId, HistoryPosition.initial)
    private val three = definition(paletteIndex(0), black, red, green)

    @Test
    fun `begin holds the document definition by reference with an empty draft history`() {
        val session = PaletteEditSession.begin(base, definition)

        assertSame(base, session.base)
        assertSame(definition, session.draft)
        assertTrue(session.timeline.isEmpty())
        assertEquals(0, session.cursor)
    }

    @Test
    fun `base and session equality use every base element`() {
        val same = RuntimeSourceToken(1L, defaultDocumentId, HistoryPosition.initial)
        val otherDocument = RuntimeSourceToken(1L, otherDocumentId, HistoryPosition.initial)
        val otherGeneration = RuntimeSourceToken(2L, defaultDocumentId, HistoryPosition.initial)
        val otherPosition = RuntimeSourceToken(1L, defaultDocumentId, HistoryPosition.create(1L))

        assertEquals(base, same)
        assertEquals(base.hashCode(), same.hashCode())
        assertNotEquals(base, otherDocument)
        assertNotEquals(base, otherGeneration)
        assertNotEquals(base, otherPosition)
        assertEquals(PaletteEditSession.begin(base, definition), PaletteEditSession.begin(same, definition))
        assertEquals(
            PaletteEditSession.begin(base, definition).hashCode(),
            PaletteEditSession.begin(same, definition).hashCode(),
        )
        assertNotEquals(PaletteEditSession.begin(base, definition), PaletteEditSession.begin(otherPosition, definition))
    }

    @Test
    fun `set slot color recolors one slot and keeps every index`() {
        val session = PaletteEditSession.begin(base, definition)

        val changed = changed(session.edit(PaletteDraftOperation.SetSlotColor(paletteIndex(1), black)))

        assertEquals(listOf(red, black), colors(changed.draft))
        assertEquals(paletteIndex(0), changed.draft.defaultIndex)
        val entry = changed.timeline.single()
        assertSame(definition, entry.before)
        assertSame(changed.draft, entry.after)
        assertSame(definition, entry.remap.source)
        assertSame(changed.draft, entry.remap.target)
        assertEquals(indices(0, 1), entry.remap.destinations())
        assertEquals(4L * (2 + 2) + 4L * 2 + 8L, entry.payloadBytes)
        assertEquals(1, changed.cursor)
        assertSame(base, changed.base)
    }

    @Test
    fun `set slot color to another slot RGBA keeps both slots separate`() {
        val session = PaletteEditSession.begin(base, definition)

        val changed = changed(session.edit(PaletteDraftOperation.SetSlotColor(paletteIndex(1), red)))

        assertEquals(listOf(red, red), colors(changed.draft))
        assertEquals(indices(0, 1), onlyDestinations(changed))
    }

    @Test
    fun `set slot color to the current RGBA is unchanged`() {
        val session = PaletteEditSession.begin(base, definition)

        val transition = session.edit(PaletteDraftOperation.SetSlotColor(paletteIndex(1), green))

        assertSame(PaletteDraftTransition.Unchanged, transition)
    }

    @Test
    fun `set slot color outside the draft is rejected with the draft entry count`() {
        val session = PaletteEditSession.begin(base, definition)

        val rejection = rejection(session.edit(PaletteDraftOperation.SetSlotColor(paletteIndex(2), black)))

        assertEquals(WorkspaceActionRejection.PaletteIndexOutsidePalette(paletteIndex(2), 2), rejection)
    }

    @Test
    fun `set default changes only the default index`() {
        val session = PaletteEditSession.begin(base, definition)

        val changed = changed(session.edit(PaletteDraftOperation.SetDefault(paletteIndex(1))))

        assertEquals(paletteIndex(1), changed.draft.defaultIndex)
        assertEquals(definition.palette, changed.draft.palette)
        assertEquals(indices(0, 1), onlyDestinations(changed))
    }

    @Test
    fun `set default to the current default is unchanged`() {
        val session = PaletteEditSession.begin(base, definition)

        assertSame(PaletteDraftTransition.Unchanged, session.edit(PaletteDraftOperation.SetDefault(paletteIndex(0))))
    }

    @Test
    fun `set default outside the draft is rejected with the draft entry count`() {
        val session = PaletteEditSession.begin(base, definition)

        val rejection = rejection(session.edit(PaletteDraftOperation.SetDefault(paletteIndex(2))))

        assertEquals(WorkspaceActionRejection.PaletteIndexOutsidePalette(paletteIndex(2), 2), rejection)
    }

    @Test
    fun `append slot preserves existing indices with an identity remap`() {
        val session = PaletteEditSession.begin(base, definition)

        val changed = changed(session.edit(PaletteDraftOperation.AppendSlot(black)))

        assertEquals(listOf(red, green, black), colors(changed.draft))
        assertEquals(paletteIndex(0), changed.draft.defaultIndex)
        val entry = changed.timeline.single()
        assertEquals(indices(0, 1), entry.remap.destinations())
        assertEquals(PaletteDraftPayload.bytes(definition, changed.draft), entry.payloadBytes)
        assertEquals(4L * (2 + 3) + 4L * 2 + 8L, entry.payloadBytes)
    }

    @Test
    fun `append slot accepts a duplicate RGBA as a new slot`() {
        val session = PaletteEditSession.begin(base, definition)

        val changed = changed(session.edit(PaletteDraftOperation.AppendSlot(red)))

        assertEquals(listOf(red, green, red), colors(changed.draft))
    }

    @Test
    fun `append slot above the supported maximum is rejected by the definition`() {
        val full = definitionOf((0 until PaletteLimits.MAX_ENTRY_COUNT).map { gray(it) })
        val session = PaletteEditSession.begin(base, full)

        val rejection = rejection(session.edit(PaletteDraftOperation.AppendSlot(black)))

        val expected = domainRejection(Palette.create(full.palette.entries().map { it.color } + black))
        assertEquals(draftRejected(PaletteDraftRejection.InvalidDefinition(expected)), rejection)
    }

    @Test
    fun `remove slot without a replacement sends references to the default`() {
        val session = PaletteEditSession.begin(base, three)

        val changed = changed(session.edit(PaletteDraftOperation.RemoveSlot(paletteIndex(1))))

        assertEquals(listOf(black, green), colors(changed.draft))
        assertEquals(paletteIndex(0), changed.draft.defaultIndex)
        assertEquals(indices(0, 0, 1), onlyDestinations(changed))
    }

    @Test
    fun `remove the default slot without a replacement is rejected`() {
        val session = PaletteEditSession.begin(base, three)

        val rejection = rejection(session.edit(PaletteDraftOperation.RemoveSlot(paletteIndex(0))))

        assertEquals(draftRejected(PaletteDraftRejection.ReplacementIsRemovedIndex), rejection)
    }

    @Test
    fun `remove slot from a two entry draft is rejected below the minimum`() {
        val session = PaletteEditSession.begin(base, definition)

        val rejection = rejection(session.edit(PaletteDraftOperation.RemoveSlot(paletteIndex(1))))

        assertEquals(draftRejected(PaletteDraftRejection.BelowDefinitionMinimum), rejection)
    }

    @Test
    fun `remove slot outside the draft is rejected`() {
        val session = PaletteEditSession.begin(base, three)

        val rejection = rejection(session.edit(PaletteDraftOperation.RemoveSlot(paletteIndex(3))))

        assertEquals(draftRejected(PaletteDraftRejection.RemovedIndexOutsidePalette(paletteIndex(3))), rejection)
    }

    @Test
    fun `remove the default slot with an explicit replacement moves the default with it`() {
        val session = PaletteEditSession.begin(base, three)

        val changed = changed(session.edit(PaletteDraftOperation.RemoveSlot(paletteIndex(0), paletteIndex(2))))

        assertEquals(listOf(red, green), colors(changed.draft))
        assertEquals(paletteIndex(1), changed.draft.defaultIndex)
        assertEquals(indices(1, 0, 1), onlyDestinations(changed))
    }

    @Test
    fun `reorder moves colors and default and records the inverse destinations`() {
        val redDefault = definition(paletteIndex(1), black, red, green)
        val newOrder = indices(2, 0, 1)
        val session = PaletteEditSession.begin(base, redDefault)

        val changed = changed(session.edit(PaletteDraftOperation.Reorder(newOrder)))

        assertEquals(listOf(green, black, red), colors(changed.draft))
        assertEquals(paletteIndex(2), changed.draft.defaultIndex)
        val destinations = onlyDestinations(changed)
        assertEquals(indices(1, 2, 0), destinations)
        newOrder.forEachIndexed { newIndex, oldIndex ->
            assertEquals(paletteIndex(newIndex), destinations[oldIndex.value])
        }
    }

    @Test
    fun `reorder with a size mismatch is rejected`() {
        val session = PaletteEditSession.begin(base, three)

        val rejection = rejection(session.edit(PaletteDraftOperation.Reorder(indices(0, 1))))

        assertEquals(draftRejected(PaletteDraftRejection.OrderSizeMismatch(3, 2)), rejection)
    }

    @Test
    fun `reorder with a repeated index is rejected`() {
        val session = PaletteEditSession.begin(base, three)

        val rejection = rejection(session.edit(PaletteDraftOperation.Reorder(indices(0, 0, 1))))

        assertEquals(draftRejected(PaletteDraftRejection.RepeatedOrderIndex(paletteIndex(0))), rejection)
    }

    @Test
    fun `two consecutive edits chain the timeline and advance the cursor`() {
        val session = PaletteEditSession.begin(base, definition)

        val first = changed(session.edit(PaletteDraftOperation.AppendSlot(black)))
        val second = changed(first.edit(PaletteDraftOperation.SetDefault(paletteIndex(2))))

        assertEquals(2, second.timeline.size)
        assertEquals(2, second.cursor)
        assertSame(second.timeline[0].after, second.timeline[1].before)
        assertSame(second.draft, second.timeline[1].after)
        assertSame(definition, second.timeline[0].before)
        assertEquals(1, first.timeline.size)
    }

    @Test
    fun `undo restores the entry before and keeps the timeline`() {
        val edited = changed(PaletteEditSession.begin(base, definition).edit(PaletteDraftOperation.AppendSlot(black)))
        val entry = edited.timeline.single()

        val undone = changed(edited.undo())

        assertSame(entry.before, undone.draft)
        assertEquals(0, undone.cursor)
        assertEquals(1, undone.timeline.size)
        assertSame(entry, undone.timeline.single())
        assertSame(base, undone.base)
    }

    @Test
    fun `undo at the start of the draft history is rejected without changing the session`() {
        val session = PaletteEditSession.begin(base, definition)

        val rejection = rejection(session.undo())

        assertEquals(draftRejected(PaletteDraftRejection.NoUndoAvailable), rejection)
        assertSame(definition, session.draft)
        assertEquals(0, session.cursor)
        assertTrue(session.timeline.isEmpty())
    }

    @Test
    fun `redo after undo restores the entry after`() {
        val edited = changed(PaletteEditSession.begin(base, definition).edit(PaletteDraftOperation.AppendSlot(black)))
        val entry = edited.timeline.single()

        val redone = changed(changed(edited.undo()).redo())

        assertSame(entry.after, redone.draft)
        assertEquals(1, redone.cursor)
        assertSame(entry, redone.timeline.single())
        assertEquals(edited, redone)
    }

    @Test
    fun `redo at the end of the draft history is rejected without changing the session`() {
        val edited = changed(PaletteEditSession.begin(base, definition).edit(PaletteDraftOperation.AppendSlot(black)))

        val rejection = rejection(edited.redo())

        assertEquals(draftRejected(PaletteDraftRejection.NoRedoAvailable), rejection)
        assertEquals(edited.timeline.size, edited.cursor)
        assertSame(edited.timeline.single().after, edited.draft)
    }

    @Test
    fun `an edit after undo discards the redo branch`() {
        val first = changed(PaletteEditSession.begin(base, definition).edit(PaletteDraftOperation.AppendSlot(black)))
        val second = changed(first.edit(PaletteDraftOperation.SetDefault(paletteIndex(2))))
        val undone = changed(second.undo())

        val branched = changed(undone.edit(PaletteDraftOperation.SetDefault(paletteIndex(1))))

        assertEquals(2, branched.timeline.size)
        assertEquals(2, branched.cursor)
        assertSame(first.timeline.single(), branched.timeline[0])
        assertSame(branched.timeline[0].after, branched.timeline[1].before)
        assertEquals(paletteIndex(1), branched.draft.defaultIndex)
        assertSame(branched.draft, branched.timeline[1].after)
    }

    @Test
    fun `edits above the entry maximum evict the oldest entry first`() {
        val sessions =
            (0..PixelLimits.MAX_HISTORY_ENTRIES).runningFold(PaletteEditSession.begin(base, definition)) { session, _ ->
                changed(session.edit(PaletteDraftOperation.AppendSlot(black)))
            }
        val last = sessions.last()

        assertEquals(PixelLimits.MAX_HISTORY_ENTRIES + 1, sessions.size - 1)
        assertEquals(PixelLimits.MAX_HISTORY_ENTRIES, last.timeline.size)
        assertEquals(PixelLimits.MAX_HISTORY_ENTRIES, last.cursor)
        assertSame(sessions[1].draft, last.timeline[0].before)
        assertSame(sessions[2].draft, last.timeline[0].after)
        val oldest = (1..PixelLimits.MAX_HISTORY_ENTRIES).fold(last) { session, _ -> changed(session.undo()) }
        assertEquals(0, oldest.cursor)
        assertSame(sessions[1].draft, oldest.draft)
        assertEquals(draftRejected(PaletteDraftRejection.NoUndoAvailable), rejection(oldest.undo()))
    }

    @Test
    fun `draft entry payload stays far below the retained payload budget`() {
        val full = definitionOf((0 until PaletteLimits.MAX_ENTRY_COUNT).map { gray(it) })

        assertEquals(36L, PaletteDraftPayload.bytes(definition, three))
        assertEquals(3080L, PaletteDraftPayload.bytes(full, full))
        assertTrue(PaletteDraftPayload.bytes(full, full) < PixelLimits.MAX_RETAINED_PAYLOAD_BYTES)
    }

    @Test
    fun `composed remap without draft history is the identity on the draft`() {
        val session = PaletteEditSession.begin(base, definition)

        val remap = session.composedRemap()

        assertSame(session.draft, remap.source)
        assertSame(session.draft, remap.target)
        assertEquals(indices(0, 1), remap.destinations())
        assertTrue(session.isIdentity())
    }

    @Test
    fun `composed remap after one reorder is that reorder from the begin definition`() {
        val session = PaletteEditSession.begin(base, definition)

        val changed = changed(session.edit(PaletteDraftOperation.Reorder(indices(1, 0))))
        val remap = changed.composedRemap()

        assertSame(definition, remap.source)
        assertEquals(changed.draft, remap.target)
        assertEquals(indices(1, 0), remap.destinations())
        assertFalse(changed.isIdentity())
    }

    @Test
    fun `composed remap chains a reorder and a remove slot in order`() {
        val session = PaletteEditSession.begin(base, three)

        val reordered = changed(session.edit(PaletteDraftOperation.Reorder(indices(2, 0, 1))))
        val removed = changed(reordered.edit(PaletteDraftOperation.RemoveSlot(paletteIndex(0), paletteIndex(1))))
        val remap = removed.composedRemap()

        // Reorder [2, 0, 1]: old 0 -> 1, old 1 -> 2, old 2 -> 0.
        // RemoveSlot(removed = 0, replacement = 1) on the reordered draft: new 0 -> 0 (replacement 1 shifted down),
        // new 1 -> 0, new 2 -> 1.
        // Composed: old 0 -> 1 -> 0, old 1 -> 2 -> 1, old 2 -> 0 -> 0.
        assertSame(three, remap.source)
        assertEquals(removed.draft, remap.target)
        assertEquals(listOf(black, red), colors(remap.target))
        assertEquals(indices(0, 1, 0), remap.destinations())
        assertFalse(removed.isIdentity())
    }

    @Test
    fun `composed remap after undo to the start keeps the first before as source and is the identity`() {
        val session = PaletteEditSession.begin(base, definition)
        val edited = changed(session.edit(PaletteDraftOperation.Reorder(indices(1, 0))))

        val undone = changed(edited.undo())
        val remap = undone.composedRemap()

        assertEquals(0, undone.cursor)
        assertSame(undone.timeline[0].before, remap.source)
        assertSame(definition, remap.source)
        assertEquals(indices(0, 1), remap.destinations())
        assertTrue(undone.isIdentity())
    }

    @Test
    fun `composed remap after set slot color keeps every index but is not the identity`() {
        val session = PaletteEditSession.begin(base, definition)

        val changed = changed(session.edit(PaletteDraftOperation.SetSlotColor(paletteIndex(1), black)))
        val remap = changed.composedRemap()

        assertSame(definition, remap.source)
        assertEquals(indices(0, 1), remap.destinations())
        assertFalse(changed.isIdentity())
    }

    private fun changed(transition: PaletteDraftTransition): PaletteEditSession =
        when (transition) {
            is PaletteDraftTransition.Changed -> transition.session
            else -> fail("Expected Changed but was $transition.")
        }

    private fun rejection(transition: PaletteDraftTransition): WorkspaceActionRejection =
        when (transition) {
            is PaletteDraftTransition.Rejected -> transition.rejection
            else -> fail("Expected Rejected but was $transition.")
        }

    private fun draftRejected(reason: PaletteDraftRejection): WorkspaceActionRejection =
        WorkspaceActionRejection.PaletteDraftRejected(reason)

    private fun onlyDestinations(session: PaletteEditSession): List<PaletteIndex> =
        session.timeline
            .single()
            .remap
            .destinations()

    private fun colors(definition: PaletteDefinition): List<PixelColor> = definition.palette.entries().map { it.color }

    private fun indices(vararg values: Int): List<PaletteIndex> = values.map { paletteIndex(it) }

    private fun definitionOf(colors: List<PixelColor>): PaletteDefinition =
        value(PaletteDefinition.create(value(Palette.create(colors)), paletteIndex(0)))

    private fun gray(level: Int): PixelColor {
        val channel = value(ColorChannel.create(level))
        return PixelColor.create(red = channel, green = channel, blue = channel, alpha = channel)
    }

    private fun domainRejection(result: DomainValueResult<*>): DomainValueRejection =
        when (result) {
            is DomainValueResult.Created -> fail("Expected Rejected but was $result.")
            is DomainValueResult.Rejected -> result.rejection
        }

    private fun <T> value(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Test value was rejected: ${result.rejection}")
        }
}
