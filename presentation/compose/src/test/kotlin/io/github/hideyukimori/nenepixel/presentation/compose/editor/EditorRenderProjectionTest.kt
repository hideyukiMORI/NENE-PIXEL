package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class EditorRenderProjectionTest {
    @Test
    fun `notice changes publish a new value without changing an earlier projection`() {
        val scene = fixture()
        val adapter = EditorRuntimeAdapter(scene.runtime)
        val initial = adapter.renderState
        val notice = PaletteEditorNotice.Rejected(WorkspaceActionRejection.NoPaletteSession)
        adapter.paletteNotice = notice
        val raised = adapter.renderState
        val equivalent = adapter.renderState
        adapter.paletteNotice = null
        val cleared = adapter.renderState

        assertNull(initial.paletteNotice)
        assertSame(notice, raised.paletteNotice)
        assertNotEquals(initial, raised)
        assertEquals(raised, equivalent)
        assertEquals(raised.hashCode(), equivalent.hashCode())
        assertEquals(initial, cleared)
        assertEquals(initial.hashCode(), cleared.hashCode())
        assertSame(notice, raised.paletteNotice)
        assertSame(initial.document, raised.document)
        assertEquals(initial.picture, raised.picture)
    }

    @Test
    fun `selection notice requires a live session while frame notices remain independent`() {
        val scene = fixture()
        val adapter = EditorRuntimeAdapter(scene.runtime)
        val frameNotice = FrameEditorNotice.Rejected(WorkspaceActionRejection.PersistenceBusy)
        adapter.selectionNotice = SelectionEditorNotice.Rejected(WorkspaceActionRejection.PersistenceBusy)
        adapter.frameNotice = frameNotice

        val projected = adapter.renderState
        adapter.frameNotice = null

        assertNull(projected.selectionSession)
        assertNull(projected.selectionNotice)
        assertSame(frameNotice, projected.frameNotice)
        assertNull(adapter.renderState.frameNotice)
    }
}
