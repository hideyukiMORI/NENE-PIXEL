package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.workspace.ActualSizeScale
import io.github.hideyukimori.nenepixel.core.application.workspace.ActualSizeWindow
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorAppearance
import io.github.hideyukimori.nenepixel.core.application.workspace.WindowAnchor
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class ActualSizeWindowRuntimeTest {
    private val window =
        ActualSizeWindow.initial
            .toggled()
            .withScale(ActualSizeScale.X4)
            .withAnchor(WindowAnchor.create(0.25, 0.75))

    @Test
    fun `new and loaded documents retain the session actual-size window`() =
        runBlocking {
            val fixture = initializedFixture()
            fixture.runtime.reduce(WorkspaceAction.SetActualSizeWindow(window))
            fixture.workflow.createNewDocument(newRequest(3, 2))
            assertEquals(canvas(3, 2), fixture.runtime.state.documentState.size)
            assertEquals(window, fixture.runtime.state.workspaceState.actualSizeWindow)
            val document = state(canvas(6, 2))
            fixture.storage.loadHandler = { ProjectLoadOutcome.Loaded(DocumentImportSource.Current(document)) }
            fixture.workflow.load()
            assertEquals(document, fixture.runtime.state.documentState)
            assertEquals(window, fixture.runtime.state.workspaceState.actualSizeWindow)
            assertEquals(EditorAppearance.initial, fixture.runtime.state.workspaceState.appearance)
        }

    @Test
    fun `the window leaves the document history checkpoint and pending autosave untouched`() =
        runBlocking {
            val fixture = initializedFixture()
            apply(fixture.runtime, position(1, 1), red)
            val before = fixture.runtime.state
            val pending = fixture.workflow.autosave.value
            fixture.runtime.reduce(WorkspaceAction.SetActualSizeWindow(window))
            val after = fixture.runtime.state
            assertSame(before.documentState, after.documentState)
            assertEquals(before.historyAvailability, after.historyAvailability)
            assertEquals(before.dirtyState, after.dirtyState)
            assertEquals(pending, fixture.workflow.autosave.value)
            assertEquals(window, after.workspaceState.actualSizeWindow)
            undo(fixture.runtime)
            assertEquals(window, fixture.runtime.state.workspaceState.actualSizeWindow)
        }
}
