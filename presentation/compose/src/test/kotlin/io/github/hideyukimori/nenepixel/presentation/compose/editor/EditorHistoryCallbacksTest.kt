package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfacePoint
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.cellAt
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class EditorHistoryCallbacksTest {
    @Test
    fun `callback drawing undo and redo publish the same canonical document changes`() {
        val scene = fixture()
        val callbacks = scene.controller.callbacks
        val surface = viewport(ViewportSurface.create(400, 400, 2.0))
        val point = viewport(ViewportSurfacePoint.create(50.0, 50.0))
        callbacks.onPointerDown(surface, point)
        val painted = callbacks.onPointerEnd(surface, point).renderState
        assertSame(painted, scene.controller.renderState)
        assertTrue(painted.canUndo)

        val undone = callbacks.onUndo()
        assertSame(undone, scene.controller.renderState)
        assertFalse(undone.canUndo)
        assertTrue(undone.canRedo)
        assertEquals(cellAt(scene.initialDocument, position(0, 0)), cellAt(undone.document, position(0, 0)))

        val redone = callbacks.onRedo()
        assertSame(redone, scene.controller.renderState)
        assertTrue(redone.canUndo)
        assertFalse(redone.canRedo)
        assertEquals(cellAt(painted.document, position(0, 0)), cellAt(redone.document, position(0, 0)))
    }

    private fun <T> viewport(result: ViewportValueResult<T>): T =
        when (result) {
            is ViewportValueResult.Created -> result.value
            is ViewportValueResult.Rejected -> error("Viewport fixture rejected: ${result.rejection}")
        }
}
