package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorAppearance
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorControlEdge
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorLayout
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorTheme
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class EditorAppearanceRuntimeTest {
    private val appearance = EditorAppearance(EditorTheme.Light, EditorLayout.Handheld, EditorControlEdge.Left)

    @Test
    fun `accepted recovery retains appearance while installing the recovered document`() =
        runBlocking {
            val document = state(canvas(3, 2))
            val fixture = Fixture(RecoveryInspection.Candidate(generation(9), document))
            fixture.initialize()
            fixture.runtime.reduce(WorkspaceAction.SetAppearance(appearance))
            assertCompleted(PersistenceLastOutcome.Recovered, fixture.workflow.acceptRecovery())
            assertEquals(document, fixture.runtime.state.documentState)
            assertEquals(appearance, fixture.runtime.state.workspaceState.appearance)
        }

    @Test
    fun `appearance preserves committed document history checkpoint and pending autosave`() =
        runBlocking {
            val fixture = initializedFixture()
            apply(fixture.runtime, position(1, 1), red)
            val before = fixture.runtime.state
            val pending = fixture.workflow.autosave.value
            fixture.runtime.reduce(WorkspaceAction.SetAppearance(appearance))
            val after = fixture.runtime.state
            assertSame(before.documentState, after.documentState)
            assertEquals(before.historyAvailability, after.historyAvailability)
            assertEquals(before.dirtyState, after.dirtyState)
            assertEquals(pending, fixture.workflow.autosave.value)
            undo(fixture.runtime)
            assertEquals(appearance, fixture.runtime.state.workspaceState.appearance)
        }

    @Test
    fun `new and loaded documents retain the session appearance`() =
        runBlocking {
            val fixture = initializedFixture()
            fixture.runtime.reduce(WorkspaceAction.SetAppearance(appearance))
            fixture.workflow.createNewDocument(newRequest(3, 2))
            assertEquals(canvas(3, 2), fixture.runtime.state.documentState.size)
            assertEquals(appearance, fixture.runtime.state.workspaceState.appearance)
            val document = state(canvas(6, 2))
            fixture.storage.loadHandler = { ProjectLoadOutcome.Loaded(document) }
            fixture.workflow.load()
            assertEquals(document, fixture.runtime.state.documentState)
            assertEquals(appearance, fixture.runtime.state.workspaceState.appearance)
        }
}
