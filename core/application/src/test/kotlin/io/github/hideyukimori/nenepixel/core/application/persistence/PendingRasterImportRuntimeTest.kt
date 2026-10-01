package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.PendingRasterImport
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

/** ADR 0033: the pending import belongs to the session's current document; replacing the document drops it. */
internal class PendingRasterImportRuntimeTest {
    @Test
    fun `new and loaded documents drop the pending import`() =
        runBlocking {
            val fixture = initializedFixture()
            val pending = pending()
            fixture.runtime.reduce(WorkspaceAction.SetPendingRasterImport(pending))
            assertSame(pending, fixture.runtime.state.workspaceState.pendingImport)
            fixture.workflow.createNewDocument(newRequest(3, 2))
            assertEquals(canvas(3, 2), fixture.runtime.state.documentState.size)
            assertNull(fixture.runtime.state.workspaceState.pendingImport)
            val again = pending()
            fixture.runtime.reduce(WorkspaceAction.SetPendingRasterImport(again))
            assertSame(again, fixture.runtime.state.workspaceState.pendingImport)
            val document = state(canvas(6, 2))
            fixture.storage.loadHandler = { ProjectLoadOutcome.Loaded(DocumentImportSource.Current(document)) }
            fixture.workflow.load()
            assertEquals(document, fixture.runtime.state.documentState)
            assertNull(fixture.runtime.state.workspaceState.pendingImport)
        }

    @Test
    fun `accepted recovery drops the pending import`() =
        runBlocking {
            val document = state(canvas(3, 2))
            val fixture = Fixture(RecoveryInspection.Candidate(generation(9), DocumentImportSource.Current(document)))
            fixture.initialize()
            val pending = pending()
            fixture.runtime.reduce(WorkspaceAction.SetPendingRasterImport(pending))
            assertSame(pending, fixture.runtime.state.workspaceState.pendingImport)
            assertCompleted(PersistenceLastOutcome.Recovered, fixture.workflow.acceptRecovery())
            assertEquals(document, fixture.runtime.state.documentState)
            assertNull(fixture.runtime.state.workspaceState.pendingImport)
        }

    private fun pending(): PendingRasterImport =
        when (val result = ImportRaster.create(1, 1, intArrayOf(RED_PIXEL))) {
            is DomainValueResult.Created -> PendingRasterImport.planned(result.value, canvas(1, 1), defaultDefinition)
            is DomainValueResult.Rejected -> fail("Raster rejected: ${result.rejection}")
        }

    private companion object {
        const val RED_PIXEL: Int = 0xff0000ff.toInt()
    }
}
