package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.palette
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class EditorAppearanceReducerTest {
    private val reducer = WorkspaceReducer.create(palette(red))
    private val initial = WorkspaceState.create(canvas(4, 4))

    @Test
    fun `all closed appearance combinations preserve selection and viewport`() {
        EditorTheme.entries.forEach { theme ->
            EditorLayout.entries.forEach { layout ->
                EditorControlEdge.entries.forEach { edge ->
                    val appearance = EditorAppearance(theme, layout, edge)
                    val next = reducer.reduce(initial, WorkspaceAction.SetAppearance(appearance)).nextState
                    assertEquals(appearance, next.appearance)
                    assertSame(initial.viewport, next.viewport)
                    assertEquals(initial.activePaletteIndex, next.activePaletteIndex)
                    assertEquals(initial.activeTool, next.activeTool)
                    assertNull(next.preview)
                    assertEquals(next, reducer.reduce(initial, WorkspaceAction.SetAppearance(appearance)).nextState)
                }
            }
        }
    }

    @Test
    fun `repeated appearance preserves state identity but cancels an active preview`() {
        val action = WorkspaceAction.SetAppearance(initial.appearance)
        val unchanged =
            assertInstanceOf(WorkspaceReductionResult.Unchanged::class.java, reducer.reduce(initial, action))
        assertEquals(WorkspaceNoChangeReason.AppearanceAlreadySet, unchanged.reason)
        assertSame(initial, unchanged.nextState)
        val preview =
            reducer
                .reduce(
                    initial,
                    WorkspaceAction.BeginGesturePreview(canvas(4, 4), position(1, 1)),
                ).nextState
        val reduced = assertInstanceOf(WorkspaceReductionResult.Reduced::class.java, reducer.reduce(preview, action))
        assertNull(reduced.nextState.preview)
        assertEquals(initial, reduced.nextState)
    }
}
