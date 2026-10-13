package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionSession
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class WorkspaceConstructionContractTest {
    @Test
    fun `view changes share unchanged sessions while selection cancellation replaces them`() {
        val size = canvas(2, 1)
        val initial = WorkspaceState.create(size)
        val changed = initial.withAppearance(initial.appearance.copy(theme = EditorTheme.Light))
        val admission = CommandGateway.create(state(size)).captureSource()
        val selecting = initial.withSelectionSession(SelectionSession.begin(admission, LayerId.first()))
        val cancelled = selecting.withSelectionSession(null)

        assertSame(initial.sessions, changed.sessions)
        assertNotSame(initial.view, changed.view)
        assertNotSame(selecting.sessions, cancelled.sessions)
        assertNull(cancelled.selectionSession)
    }

    @Test
    fun `gesture preview copies share every unchanged immutable workspace group`() {
        val size = canvas(2, 1)
        val initial = WorkspaceState.create(size)
        val admission = CommandGateway.create(state(size)).captureSource()
        val gesture =
            ToolGesture.begin(
                ToolGestureAdmission(size, StrokeEffect.Paint(redIndex), LayerId.first(), admission),
                position(0, 0),
            )
        val previewing = initial.withPreview(gesture)
        val finished = previewing.withPreview(null)

        assertSame(initial.editing, previewing.editing)
        assertSame(initial.view, previewing.view)
        assertSame(initial.sessions, previewing.sessions)
        assertSame(gesture, previewing.preview)
        assertEquals(initial, finished)
        assertEquals(initial.hashCode(), finished.hashCode())
        assertEquals(initial.toString(), finished.toString())
    }
}
