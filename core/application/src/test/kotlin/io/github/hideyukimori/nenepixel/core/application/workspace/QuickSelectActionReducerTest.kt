package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandSourceAdmission
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.definition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.reduced
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.rejected
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.unchanged
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.EyedropperState
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelectItem
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelection
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class QuickSelectActionReducerTest {
    private val canvas = canvas(2, 1)
    private val reducer = WorkspaceReducer.create()

    /** Slots 0 and 2 hold the same RGBA; pixel (0, 0) is painted with slot 1 and pixel (1, 0) with slot 2. */
    private val admission: CommandSourceAdmission =
        CommandGateway
            .create(
                state(
                    canvas,
                    indices = listOf(paletteIndex(1), paletteIndex(2)),
                    definition = definition(paletteIndex(0), red, green, red),
                ),
            ).captureSource()

    private val idle = WorkspaceState.create(canvas)

    @Test
    fun `open shows recent slots then the eyedropper with nothing highlighted`() {
        val opened = openedWith(paletteIndex(1))

        val menu = checkNotNull(opened.quickSelection.menu)
        assertEquals(listOf(QuickSelectItem.PaletteSlot(paletteIndex(1)), QuickSelectItem.Eyedropper), menu.items)
        assertNull(menu.highlighted)
    }

    @Test
    fun `open is rejected while a gesture preview exists`() {
        val previewing = reduced(reduce(idle, WorkspaceAction.BeginGesturePreview(canvas, position(0, 0))))

        val result = rejected(reduce(previewing, WorkspaceAction.OpenQuickSelect))

        assertEquals(WorkspaceActionRejection.PreviewAlreadyActive, result.rejection)
        assertSame(previewing, result.nextState)
    }

    @Test
    fun `open is rejected while the eyedropper is armed`() {
        val result = rejected(reduce(armed(), WorkspaceAction.OpenQuickSelect))

        assertEquals(WorkspaceActionRejection.EyedropperArmed, result.rejection)
    }

    @Test
    fun `open is unchanged when the menu is already open`() {
        val opened = opened()

        val result = unchanged(reduce(opened, WorkspaceAction.OpenQuickSelect))

        assertEquals(WorkspaceNoChangeReason.QuickSelectMenuAlreadyOpen, result.reason)
        assertSame(opened, result.nextState)
    }

    @Test
    fun `highlight is rejected when the menu is closed`() {
        val result = rejected(reduce(idle, WorkspaceAction.HighlightQuickSelectItem(QuickSelectItem.Eyedropper)))

        assertEquals(WorkspaceActionRejection.NoQuickSelectMenu, result.rejection)
    }

    @Test
    fun `highlight is rejected for an item that is not in the menu`() {
        val opened = opened()
        val item = QuickSelectItem.PaletteSlot(paletteIndex(2))

        val result = rejected(reduce(opened, WorkspaceAction.HighlightQuickSelectItem(item)))

        assertEquals(WorkspaceActionRejection.QuickSelectItemNotInMenu, result.rejection)
        assertSame(opened, result.nextState)
    }

    @Test
    fun `highlight is unchanged for the current highlight`() {
        val result = unchanged(reduce(opened(), WorkspaceAction.HighlightQuickSelectItem(null)))

        assertEquals(WorkspaceNoChangeReason.QuickSelectHighlightUnchanged, result.reason)
    }

    @Test
    fun `highlight changes only the highlighted item`() {
        val opened = opened()

        val highlighted = highlight(opened, QuickSelectItem.Eyedropper)

        assertEquals(QuickSelectItem.Eyedropper, highlighted.quickSelection.menu?.highlighted)
        assertEquals(opened.quickSelection.menu?.items, highlighted.quickSelection.menu?.items)
        assertEquals(opened.activePaletteIndex, highlighted.activePaletteIndex)
    }

    @Test
    fun `confirm is rejected when the menu is closed`() {
        val result = rejected(reduce(idle, WorkspaceAction.ConfirmQuickSelect))

        assertEquals(WorkspaceActionRejection.NoQuickSelectMenu, result.rejection)
    }

    @Test
    fun `confirming a palette slot selects it and closes the menu`() {
        val highlighted = highlight(opened(), QuickSelectItem.PaletteSlot(paletteIndex(1)))

        val confirmed = reduced(reduce(highlighted, WorkspaceAction.ConfirmQuickSelect))

        assertEquals(paletteIndex(1), confirmed.activePaletteIndex)
        assertNull(confirmed.quickSelection.menu)
        assertEquals(EyedropperState.Idle, confirmed.quickSelection.eyedropper)
    }

    @Test
    fun `confirming the active slot still closes the menu as reduced`() {
        val highlighted = highlight(openedWith(paletteIndex(0)), QuickSelectItem.PaletteSlot(paletteIndex(0)))

        val confirmed = reduced(reduce(highlighted, WorkspaceAction.ConfirmQuickSelect))

        assertEquals(paletteIndex(0), confirmed.activePaletteIndex)
        assertNull(confirmed.quickSelection.menu)
    }

    @Test
    fun `confirming a slot outside the palette is rejected and keeps the menu open`() {
        val highlighted = highlight(openedWith(paletteIndex(5)), QuickSelectItem.PaletteSlot(paletteIndex(5)))

        val result = rejected(reduce(highlighted, WorkspaceAction.ConfirmQuickSelect))

        val rejection = result.rejection as WorkspaceActionRejection.PaletteIndexOutsidePalette
        assertEquals(paletteIndex(5), rejection.attemptedIndex)
        assertEquals(3, rejection.entryCount)
        assertSame(highlighted, result.nextState)
    }

    @Test
    fun `confirming the eyedropper arms it and closes the menu`() {
        val confirmed = armed()

        assertEquals(EyedropperState.Armed, confirmed.quickSelection.eyedropper)
        assertNull(confirmed.quickSelection.menu)
        assertEquals(paletteIndex(0), confirmed.activePaletteIndex)
    }

    @Test
    fun `confirming without a highlight only closes the menu`() {
        val opened = opened()

        val confirmed = reduced(reduce(opened, WorkspaceAction.ConfirmQuickSelect))

        assertEquals(opened.withQuickSelection(opened.quickSelection.closed()), confirmed)
    }

    @Test
    fun `cancel is unchanged when the menu is closed`() {
        val result = unchanged(reduce(idle, WorkspaceAction.CancelQuickSelect))

        assertEquals(WorkspaceNoChangeReason.QuickSelectMenuAlreadyClosed, result.reason)
        assertSame(idle, result.nextState)
    }

    @Test
    fun `cancel closes the menu and changes nothing else`() {
        val highlighted = highlight(opened(), QuickSelectItem.PaletteSlot(paletteIndex(1)))

        val cancelled = reduced(reduce(highlighted, WorkspaceAction.CancelQuickSelect))

        assertEquals(highlighted.withQuickSelection(highlighted.quickSelection.closed()), cancelled)
        assertEquals(paletteIndex(0), cancelled.activePaletteIndex)
    }

    @Test
    fun `pick is rejected while the eyedropper is idle`() {
        val result = rejected(reduce(idle, WorkspaceAction.PickPaletteEntryAt(position(1, 0))))

        assertEquals(WorkspaceActionRejection.EyedropperNotArmed, result.rejection)
        assertSame(idle, result.nextState)
    }

    @Test
    fun `pick outside the canvas is rejected and stays armed`() {
        val armed = armed()

        val result = rejected(reduce(armed, WorkspaceAction.PickPaletteEntryAt(position(2, 0))))

        val rejection = result.rejection as WorkspaceActionRejection.PickPositionOutsideCanvas
        assertEquals(canvas, rejection.canvas)
        assertEquals(position(2, 0), rejection.position)
        assertSame(armed, result.nextState)
        assertEquals(EyedropperState.Armed, result.nextState.quickSelection.eyedropper)
    }

    @Test
    fun `pick selects the painted slot among duplicate colours and returns to idle`() {
        val picked = reduced(reduce(armed(), WorkspaceAction.PickPaletteEntryAt(position(1, 0))))

        assertEquals(paletteIndex(2), picked.activePaletteIndex)
        assertEquals(EyedropperState.Idle, picked.quickSelection.eyedropper)
    }

    @Test
    fun `picking the active slot is still reduced to idle`() {
        val activeTwo = armed().withActivePaletteIndex(paletteIndex(2))

        val picked = reduced(reduce(activeTwo, WorkspaceAction.PickPaletteEntryAt(position(1, 0))))

        assertEquals(paletteIndex(2), picked.activePaletteIndex)
        assertEquals(EyedropperState.Idle, picked.quickSelection.eyedropper)
    }

    @Test
    fun `disarm is unchanged while idle`() {
        val result = unchanged(reduce(idle, WorkspaceAction.DisarmEyedropper))

        assertEquals(WorkspaceNoChangeReason.EyedropperAlreadyIdle, result.reason)
    }

    @Test
    fun `disarm returns the armed eyedropper to idle`() {
        val armed = armed()

        val disarmed = reduced(reduce(armed, WorkspaceAction.DisarmEyedropper))

        assertEquals(armed.withQuickSelection(armed.quickSelection.idle()), disarmed)
    }

    @Test
    fun `gesture preview begin is rejected while the menu is open`() {
        val opened = opened()

        val result = rejected(reduce(opened, WorkspaceAction.BeginGesturePreview(canvas, position(0, 0))))

        assertEquals(WorkspaceActionRejection.QuickSelectMenuOpen, result.rejection)
        assertSame(opened, result.nextState)
    }

    @Test
    fun `gesture preview begin is rejected while the eyedropper is armed`() {
        val armed = armed()

        val result = rejected(reduce(armed, WorkspaceAction.BeginGesturePreview(canvas, position(0, 0))))

        assertEquals(WorkspaceActionRejection.EyedropperArmed, result.rejection)
        assertNull(result.nextState.preview)
    }

    @Test
    fun `selecting a tool disarms the eyedropper even for the current tool`() {
        val same = reduced(reduce(armed(), WorkspaceAction.SelectTool(DrawingTool.Pencil)))
        val other = reduced(reduce(armed(), WorkspaceAction.SelectTool(DrawingTool.Eraser)))

        assertEquals(EyedropperState.Idle, same.quickSelection.eyedropper)
        assertEquals(DrawingTool.Pencil, same.activeTool)
        assertEquals(EyedropperState.Idle, other.quickSelection.eyedropper)
        assertEquals(DrawingTool.Eraser, other.activeTool)
    }

    private fun openedWith(recent: PaletteIndex): WorkspaceState {
        val start = idle.withQuickSelection(QuickSelection.initial.withRecent(listOf(recent)))
        return reduced(reduce(start, WorkspaceAction.OpenQuickSelect))
    }

    private fun opened(): WorkspaceState = openedWith(paletteIndex(1))

    private fun highlight(
        state: WorkspaceState,
        item: QuickSelectItem,
    ): WorkspaceState = reduced(reduce(state, WorkspaceAction.HighlightQuickSelectItem(item)))

    private fun armed(): WorkspaceState =
        reduced(reduce(highlight(opened(), QuickSelectItem.Eyedropper), WorkspaceAction.ConfirmQuickSelect))

    private fun reduce(
        state: WorkspaceState,
        action: WorkspaceAction,
    ): WorkspaceReductionResult = reducer.reduce(state, action, admission)
}
