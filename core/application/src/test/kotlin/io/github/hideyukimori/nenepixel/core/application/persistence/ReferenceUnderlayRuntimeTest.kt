package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** ADR 0032: the underlay belongs to the session's current document; replacing the document drops it. */
internal class ReferenceUnderlayRuntimeTest {
    private val underlay = ReferenceUnderlay.placed(image(8, 4), canvas(4, 4))

    @Test
    fun `new and loaded documents drop the underlay`() =
        runBlocking {
            val fixture = initializedFixture()
            fixture.runtime.reduce(WorkspaceAction.SetReferenceUnderlay(underlay))
            assertEquals(underlay, fixture.runtime.state.workspaceState.underlay)
            fixture.workflow.createNewDocument(newRequest(3, 2))
            assertEquals(canvas(3, 2), fixture.runtime.state.documentState.size)
            assertNull(fixture.runtime.state.workspaceState.underlay)
            fixture.runtime.reduce(WorkspaceAction.SetReferenceUnderlay(underlay))
            val document = state(canvas(6, 2))
            fixture.storage.loadHandler = { ProjectLoadOutcome.Loaded(DocumentImportSource.Current(document)) }
            fixture.workflow.load()
            assertEquals(document, fixture.runtime.state.documentState)
            assertNull(fixture.runtime.state.workspaceState.underlay)
        }

    @Test
    fun `accepted recovery drops the underlay`() =
        runBlocking {
            val document = state(canvas(3, 2))
            val fixture = Fixture(RecoveryInspection.Candidate(generation(9), DocumentImportSource.Current(document)))
            fixture.initialize()
            fixture.runtime.reduce(WorkspaceAction.SetReferenceUnderlay(underlay))
            assertEquals(underlay, fixture.runtime.state.workspaceState.underlay)
            assertCompleted(PersistenceLastOutcome.Recovered, fixture.workflow.acceptRecovery())
            assertEquals(document, fixture.runtime.state.documentState)
            assertNull(fixture.runtime.state.workspaceState.underlay)
        }

    @Test
    fun `the runtime reduces both underlay actions while a palette session is open`() {
        runBlocking {
            val fixture = initializedFixture()
            assertInstanceOf(
                WorkspaceReductionResult.Reduced::class.java,
                fixture.runtime.paletteOperations.beginPaletteEdit(),
            )
            assertInstanceOf(
                WorkspaceReductionResult.Reduced::class.java,
                fixture.runtime.reduce(WorkspaceAction.SetReferenceUnderlay(underlay)),
            )
            assertEquals(underlay, fixture.runtime.state.workspaceState.underlay)
            assertInstanceOf(
                WorkspaceReductionResult.Reduced::class.java,
                fixture.runtime.reduce(WorkspaceAction.ClearReferenceUnderlay),
            )
            assertNull(fixture.runtime.state.workspaceState.underlay)
            assertNotNull(fixture.runtime.state.workspaceState.paletteEditSession)
        }
    }

    private fun image(
        width: Int,
        height: Int,
    ): ReferenceImage {
        val result = ReferenceImage.create(width, height, IntArray(width * height))
        check(result is ReferenceImageResult.Created) { "expected Created, got $result" }
        return result.image
    }
}
