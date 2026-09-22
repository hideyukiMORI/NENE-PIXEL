package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ActualSizeWindowReducerTest {
    private val reducer = WorkspaceReducer.create()
    private val initial = WorkspaceState.create(canvas(4, 4))
    private val admission = CommandGateway.create(state(canvas(4, 4))).captureSource()

    @Test
    fun `the initial window is hidden at x2 anchored top trailing and cycles through every scale`() {
        val window = initial.actualSizeWindow
        assertFalse(window.visible)
        assertEquals(ActualSizeScale.X2, window.scale)
        assertEquals(WindowAnchor.create(1.0, 0.0), window.anchor)
        assertEquals(listOf(1, 2, 4, 8), ActualSizeScale.entries.map(ActualSizeScale::devicePixelsPerCell))
        assertEquals(
            ActualSizeScale.entries.toList(),
            generateSequence(ActualSizeScale.X1, ActualSizeScale::next).take(ActualSizeScale.entries.size).toList(),
        )
        assertEquals(ActualSizeScale.X1, ActualSizeScale.X8.next())
        assertTrue(window.toggled().visible)
        assertFalse(window.toggled().toggled().visible)
    }

    @Test
    fun `every scale is accepted and leaves the rest of the workspace untouched`() {
        ActualSizeScale.entries.forEach { scale ->
            val window = initial.actualSizeWindow.toggled().withScale(scale)
            val reduced =
                assertInstanceOf(
                    WorkspaceReductionResult.Reduced::class.java,
                    reducer.reduce(initial, WorkspaceAction.SetActualSizeWindow(window), admission),
                )
            val next = reduced.nextState
            assertEquals(window, next.actualSizeWindow)
            assertEquals(scale, next.actualSizeWindow.scale)
            assertTrue(next.actualSizeWindow.visible)
            assertSame(initial.viewport, next.viewport)
            assertEquals(initial.appearance, next.appearance)
            assertEquals(initial.activeTool, next.activeTool)
            assertEquals(initial.activePaletteIndex, next.activePaletteIndex)
        }
    }

    @Test
    fun `anchors clamp into the work area instead of being rejected`() {
        assertEquals(0.0, WindowAnchor.create(-0.5, 0.0).x)
        assertEquals(1.0, WindowAnchor.create(1.5, 0.0).x)
        assertEquals(0.0, WindowAnchor.create(0.0, Double.NaN).y)
        assertEquals(0.0, WindowAnchor.create(Double.NEGATIVE_INFINITY, 0.0).x)
        assertEquals(1.0, WindowAnchor.create(0.0, Double.POSITIVE_INFINITY).y)
        assertEquals(WindowAnchor.create(0.0, 0.0), WindowAnchor.create(-0.0, -0.0))
        assertEquals(WindowAnchor.create(0.0, 0.0).hashCode(), WindowAnchor.create(-0.0, -0.0).hashCode())
        listOf(0.0, 0.5, 1.0).forEach { value ->
            assertEquals(value, WindowAnchor.create(value, value).x)
            assertEquals(value, WindowAnchor.create(value, value).y)
        }
        val requested = initial.actualSizeWindow.withAnchor(WindowAnchor.create(2.0, -1.0))
        val next = reducer.reduce(initial, WorkspaceAction.SetActualSizeWindow(requested), admission).nextState
        assertEquals(WindowAnchor.create(1.0, 0.0), next.actualSizeWindow.anchor)
    }

    @Test
    fun `an equal window is unchanged and keeps both the state and an active gesture`() {
        val action = WorkspaceAction.SetActualSizeWindow(initial.actualSizeWindow.withScale(ActualSizeScale.X2))
        val unchanged =
            assertInstanceOf(WorkspaceReductionResult.Unchanged::class.java, reducer.reduce(initial, action, admission))
        assertEquals(WorkspaceNoChangeReason.ActualSizeWindowAlreadySet, unchanged.reason)
        assertSame(initial, unchanged.nextState)
        val drawing = beginGesture()
        val stillUnchanged =
            assertInstanceOf(WorkspaceReductionResult.Unchanged::class.java, reducer.reduce(drawing, action, admission))
        assertSame(drawing, stillUnchanged.nextState)
        assertNotNull(stillUnchanged.nextState.preview)
    }

    @Test
    fun `window changes never cancel an in-progress gesture`() {
        val drawing = beginGesture()
        val window = drawing.actualSizeWindow.toggled().withScale(ActualSizeScale.X8)
        val reduced =
            assertInstanceOf(
                WorkspaceReductionResult.Reduced::class.java,
                reducer.reduce(drawing, WorkspaceAction.SetActualSizeWindow(window), admission),
            )
        assertSame(drawing.preview, reduced.nextState.preview)
        assertEquals(window, reduced.nextState.actualSizeWindow)
        val extended =
            reducer
                .reduce(reduced.nextState, WorkspaceAction.ExtendGesturePreview(position(2, 1)), admission)
                .nextState
        assertEquals(window, extended.actualSizeWindow)
        val prepared =
            assertInstanceOf(
                WorkspaceReductionResult.CommitPrepared::class.java,
                reducer.reduce(extended, WorkspaceAction.PrepareGestureCommit, admission),
            )
        assertEquals(window, prepared.nextState.actualSizeWindow)
    }

    private fun beginGesture(): WorkspaceState =
        reducer
            .reduce(initial, WorkspaceAction.BeginGesturePreview(canvas(4, 4), position(1, 1)), admission)
            .nextState
}
