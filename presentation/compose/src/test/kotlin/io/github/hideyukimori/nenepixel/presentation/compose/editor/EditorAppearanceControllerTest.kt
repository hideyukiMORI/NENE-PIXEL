package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.EditorAppearance
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorControlEdge
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorLayout
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorTheme
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class EditorAppearanceControllerTest {
    @Test
    fun `appearance callback publishes the canonical projection without a document mutation`() {
        val fixture = fixture()
        val before = fixture.controller.renderState
        val choice = EditorAppearance(EditorTheme.Light, EditorLayout.Handheld, EditorControlEdge.Left)
        val result = fixture.controller.callbacks.onSetAppearance(choice)
        assertEquals(choice, result.appearance)
        assertEquals(choice, fixture.runtime.state.workspaceState.appearance)
        assertSame(result, fixture.controller.renderStates.value)
        assertSame(before.snapshot, result.snapshot)
        assertSame(before.viewport, result.viewport)
        assertEquals(before.dirtyState, result.dirtyState)
        assertEquals(before.canUndo, result.canUndo)
        assertEquals(before.canRedo, result.canRedo)
    }
}
