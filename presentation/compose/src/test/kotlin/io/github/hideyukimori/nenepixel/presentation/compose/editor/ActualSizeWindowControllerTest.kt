package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.ActualSizeScale
import io.github.hideyukimori.nenepixel.core.application.workspace.WindowAnchor
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ActualSizeWindowControllerTest {
    @Test
    fun `the actual-size callback publishes the canonical projection without a document mutation`() {
        val fixture = fixture()
        val before = fixture.controller.renderState
        assertFalse(before.actualSizeWindow.visible)
        val shown = before.actualSizeWindow.toggled()
        val result = fixture.controller.callbacks.onSetActualSizeWindow(shown)
        assertTrue(result.actualSizeWindow.visible)
        assertEquals(shown, result.actualSizeWindow)
        assertEquals(shown, fixture.runtime.state.workspaceState.actualSizeWindow)
        assertSame(result, fixture.controller.renderStates.value)
        assertSame(before.snapshot, result.snapshot)
        assertSame(before.viewport, result.viewport)
        assertEquals(before.appearance, result.appearance)
        assertEquals(before.dirtyState, result.dirtyState)
        assertEquals(before.canUndo, result.canUndo)
        assertEquals(before.canRedo, result.canRedo)
    }

    @Test
    fun `tapping the window cycles through every scale and returns to the first one`() {
        val fixture = fixture()
        fixture.controller.callbacks.onSetActualSizeWindow(
            fixture.controller.renderState.actualSizeWindow
                .toggled(),
        )
        val observed = mutableListOf<ActualSizeScale>()
        repeat(ActualSizeScale.entries.size + 1) {
            val window = fixture.controller.renderState.actualSizeWindow
            observed += window.scale
            fixture.controller.callbacks.onSetActualSizeWindow(window.withScale(window.scale.next()))
        }
        assertEquals(
            listOf(
                ActualSizeScale.X4,
                ActualSizeScale.X8,
                ActualSizeScale.X16,
                ActualSizeScale.X32,
                ActualSizeScale.X1,
                ActualSizeScale.X2,
                ActualSizeScale.X4,
            ),
            observed,
        )
        assertTrue(fixture.controller.renderState.actualSizeWindow.visible)
    }

    @Test
    fun `released anchors outside the work area are clamped before publication`() {
        val fixture = fixture()
        val window =
            fixture.controller.renderState.actualSizeWindow
                .toggled()
        val result =
            fixture.controller.callbacks.onSetActualSizeWindow(window.withAnchor(WindowAnchor.create(-3.0, 4.0)))
        assertEquals(WindowAnchor.create(0.0, 1.0), result.actualSizeWindow.anchor)
        val hidden = fixture.controller.callbacks.onSetActualSizeWindow(result.actualSizeWindow.toggled())
        assertFalse(hidden.actualSizeWindow.visible)
        assertEquals(WindowAnchor.create(0.0, 1.0), hidden.actualSizeWindow.anchor)
        assertSame(fixture.initialDocument, fixture.runtime.state.documentState)
    }
}
