package io.github.hideyukimori.nenepixel.core.application.workspace.palette

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPosition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.definition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeSourceToken
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapPlanner
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class PendingPaletteImportTest {
    private val base = RuntimeSourceToken(1L, defaultDocumentId, HistoryPosition.initial)
    private val three = definition(paletteIndex(0), black, red, green)
    private val target = definition(paletteIndex(1), green, black)

    @Test
    fun `staging a shorter import by number leaves the sources outside it unresolved`() {
        val staged = staged()

        val pending = checkNotNull(staged.pendingImport)
        assertSame(target, pending.target)
        assertEquals(PaletteImportMode.ByNumber, pending.mode)
        assertEquals(emptyMap<PaletteIndex, PaletteIndex>(), pending.assignments)
        assertEquals(PaletteImportResolution.Unresolved(indices(2)), pending.resolve(staged.draft))
        assertSame(three, staged.draft)
        assertEquals(0, staged.timeline.size)
    }

    @Test
    fun `an assigned by number import confirms as one draft entry and clears the pending import`() {
        val assigned = changed(staged().assignImportSlot(paletteIndex(2), paletteIndex(0)))

        val confirmed = changed(assigned.confirmImport())

        val entry = confirmed.timeline.single()
        assertEquals(indices(0, 1, 0), entry.remap.destinations())
        assertSame(three, entry.before)
        assertEquals(target, confirmed.draft)
        assertEquals(paletteIndex(1), confirmed.draft.defaultIndex)
        assertEquals(1, confirmed.cursor)
        assertNull(confirmed.pendingImport)
    }

    @Test
    fun `a nearest import confirms through the nearest remap`() {
        val nearest = changed(staged().setImportMode(PaletteImportMode.Nearest))

        val confirmed = changed(nearest.confirmImport())

        val expected =
            when (val remap = PaletteRemapPlanner.nearest(three, target)) {
                is PaletteRemapResult.Planned -> remap.remap.destinations()
                is PaletteRemapResult.Rejected -> fail("Nearest remap was rejected: ${remap.rejection}")
            }
        assertEquals(
            expected,
            confirmed.timeline
                .single()
                .remap
                .destinations(),
        )
        assertEquals(target, confirmed.draft)
        assertNull(confirmed.pendingImport)
    }

    @Test
    fun `an explicit import with one missing assignment is rejected as unresolved`() {
        val explicit = changed(staged().setImportMode(PaletteImportMode.Explicit))
        val assigned =
            changed(
                changed(explicit.assignImportSlot(paletteIndex(0), paletteIndex(1)))
                    .assignImportSlot(paletteIndex(2), paletteIndex(0)),
            )

        val rejection = rejection(assigned.confirmImport())

        assertEquals(draftRejected(PaletteDraftRejection.UnresolvedImportSources(indices(1))), rejection)
    }

    @Test
    fun `assignments outside the draft or the import are rejected`() {
        val staged = staged()

        val source = rejection(staged.assignImportSlot(paletteIndex(3), paletteIndex(0)))
        val destination = rejection(staged.assignImportSlot(paletteIndex(0), paletteIndex(2)))

        assertEquals(draftRejected(PaletteDraftRejection.ImportSourceOutsidePalette(paletteIndex(3))), source)
        assertEquals(
            draftRejected(PaletteDraftRejection.ImportDestinationOutsidePalette(paletteIndex(2))),
            destination,
        )
    }

    @Test
    fun `draft edit undo and redo wait while an import is pending`() {
        val edited = changed(PaletteEditSession.begin(base, three).edit(PaletteDraftOperation.AppendSlot(red)))
        val pending = changed(edited.stageImport(target))

        val edit = rejection(pending.edit(PaletteDraftOperation.SetDefault(paletteIndex(1))))
        val undo = rejection(pending.undo())
        val redo = rejection(pending.redo())

        val expected = draftRejected(PaletteDraftRejection.ImportPending)
        assertEquals(expected, edit)
        assertEquals(expected, undo)
        assertEquals(expected, redo)
    }

    @Test
    fun `cancelling a pending import keeps the draft and its history`() {
        val edited = changed(PaletteEditSession.begin(base, three).edit(PaletteDraftOperation.AppendSlot(red)))
        val pending = changed(edited.stageImport(target))

        val cancelled = changed(pending.cancelImport())

        assertNull(cancelled.pendingImport)
        assertSame(edited.draft, cancelled.draft)
        assertSame(edited.timeline, cancelled.timeline)
        assertEquals(edited.cursor, cancelled.cursor)
        assertEquals(edited, cancelled)
    }

    @Test
    fun `import transitions without a pending import are rejected`() {
        val session = PaletteEditSession.begin(base, three)
        val expected = draftRejected(PaletteDraftRejection.NoPendingImport)

        assertEquals(expected, rejection(session.confirmImport()))
        assertEquals(expected, rejection(session.cancelImport()))
        assertEquals(expected, rejection(session.setImportMode(PaletteImportMode.Nearest)))
        assertEquals(expected, rejection(session.assignImportSlot(paletteIndex(0), paletteIndex(0))))
    }

    private fun staged(): PaletteEditSession = changed(PaletteEditSession.begin(base, three).stageImport(target))

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

    private fun indices(vararg values: Int): List<PaletteIndex> = values.map { paletteIndex(it) }
}
