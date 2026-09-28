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
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.prepared
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.reduced
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.CanvasPointerIntent
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.EyedropperState
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelection
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class QuickSelectionReducerTest {
    private val canvas = canvas(2, 1)
    private val reducer = WorkspaceReducer.create()
    private val admission: CommandSourceAdmission =
        CommandGateway.create(state(canvas, definition = definition(paletteIndex(0), red, green))).captureSource()

    @Test
    fun `initial workspace carries the initial quick selection and draws`() {
        val initial = WorkspaceState.create(canvas)

        assertEquals(QuickSelection.initial, initial.quickSelection)
        assertEquals(CanvasPointerIntent.Draw, initial.canvasPointerIntent)
    }

    @Test
    fun `committing a paint stroke records its slot`() {
        val selected = select(WorkspaceState.create(canvas), 1)

        val first = commit(selected)
        val second = commit(select(first, 0))

        assertEquals(listOf(paletteIndex(1)), first.quickSelection.recent)
        assertEquals(listOf(paletteIndex(0), paletteIndex(1)), second.quickSelection.recent)
        assertNull(second.preview)
    }

    @Test
    fun `committing an erase stroke does not record`() {
        val erasing = reduced(reduce(WorkspaceState.create(canvas), WorkspaceAction.SelectTool(DrawingTool.Eraser)))

        val committed = commit(erasing)

        assertEquals(emptyList<PaletteIndex>(), committed.quickSelection.recent)
    }

    @Test
    fun `reconciling the palette installs the reconciled slots closes the menu and disarms`() {
        val selection =
            listOf(0, 3, 1)
                .fold(QuickSelection.initial) { current, value -> current.recordPainted(paletteIndex(value)) }
                .opened()
                .armed()
        val start = WorkspaceState.create(canvas).withQuickSelection(selection)

        val action = ReconcileDocumentPalette(paletteIndex(0), listOf(paletteIndex(0), paletteIndex(1)))

        val reconciled = reduced(reduce(start, action)).quickSelection

        assertEquals(listOf(paletteIndex(0), paletteIndex(1)), reconciled.recent)
        assertNull(reconciled.menu)
        assertEquals(EyedropperState.Idle, reconciled.eyedropper)
    }

    @Test
    fun `canvas pointer intent picks exactly while the eyedropper is armed`() {
        val initial = WorkspaceState.create(canvas)
        val armed = initial.withQuickSelection(initial.quickSelection.armed())
        val idle = armed.withQuickSelection(armed.quickSelection.idle())

        assertEquals(CanvasPointerIntent.PickPaletteEntry, armed.canvasPointerIntent)
        assertEquals(CanvasPointerIntent.Draw, idle.canvasPointerIntent)
    }

    @Test
    fun `quick selection changes keep an in-progress preview`() {
        val previewing = begin(WorkspaceState.create(canvas))

        val opened = previewing.withQuickSelection(previewing.quickSelection.opened())

        assertEquals(previewing.preview, opened.preview)
    }

    private fun select(
        state: WorkspaceState,
        index: Int,
    ): WorkspaceState = reduced(reduce(state, WorkspaceAction.SelectPaletteEntry(paletteIndex(index))))

    private fun begin(state: WorkspaceState): WorkspaceState =
        reduced(reduce(state, WorkspaceAction.BeginGesturePreview(canvas, position(0, 0))))

    private fun commit(state: WorkspaceState): WorkspaceState =
        prepared(reduce(begin(state), WorkspaceAction.PrepareGestureCommit)).nextState

    private fun reduce(
        state: WorkspaceState,
        action: WorkspaceAction,
    ): WorkspaceReductionResult = reducer.reduce(state, action, admission)
}
