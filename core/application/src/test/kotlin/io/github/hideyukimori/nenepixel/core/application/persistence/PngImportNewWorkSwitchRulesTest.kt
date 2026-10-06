package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.DocumentIdentity
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** ADR 0033: opening a pending PNG as a new work follows the same switch rules as a new work. */
internal class PngImportNewWorkSwitchRulesTest {
    @Test
    fun `an unadopted recovery candidate asks first and the confirmation opens the plan`() {
        runBlocking {
            val candidate = state(canvas(2, 2), identity = DocumentIdentity(documentId('a')))
            val fixture =
                Fixture(RecoveryInspection.Candidate(generation(9), DocumentImportSource.Current(candidate)))
            fixture.initialize()
            val plan = setPendingNewWork(fixture)
            val before = fixture.runtime.state.documentState

            val confirmation = assertAwaiting(fixture.workflow.pngImport.openAsNewWork())

            assertEquals(PersistenceConfirmationReason.DISCARD_RECOVERY_CANDIDATE, confirmation.reason)
            assertEquals(before, fixture.runtime.state.documentState)
            assertCompleted(PersistenceLastOutcome.NewDocumentCreated, fixture.workflow.confirm(confirmation))
            assertOpenedFrom(fixture, plan, before.id)
        }
    }

    @Test
    fun `a second press while the first one awaits is stale and the plan opens once`() {
        runBlocking {
            val fixture = initializedFixture()
            val plan = setPendingNewWork(fixture)
            apply(fixture.runtime, position(1, 1), red)
            val before = fixture.runtime.state.documentState
            val ids = fixture.ids.callCount

            val confirmation = assertAwaiting(fixture.workflow.pngImport.openAsNewWork())
            assertEquals(PersistenceRequestResult.Stale, fixture.workflow.pngImport.openAsNewWork())

            assertEquals(before, fixture.runtime.state.documentState)
            assertCompleted(PersistenceLastOutcome.NewDocumentCreated, fixture.workflow.confirm(confirmation))
            assertOpenedFrom(fixture, plan, before.id)
            assertEquals(ids + 1, fixture.ids.callCount)
        }
    }

    @Test
    fun `a new document id is taken once and only after the confirmation`() {
        runBlocking {
            val dirty = initializedFixture()
            setPendingNewWork(dirty)
            apply(dirty.runtime, position(1, 1), red)
            val dirtyIds = dirty.ids.callCount

            val confirmation = assertAwaiting(dirty.workflow.pngImport.openAsNewWork())
            assertEquals(dirtyIds, dirty.ids.callCount)
            assertCompleted(PersistenceLastOutcome.NewDocumentCreated, dirty.workflow.confirm(confirmation))
            assertEquals(dirtyIds + 1, dirty.ids.callCount)

            val clean = initializedFixture()
            setPendingNewWork(clean)
            val cleanIds = clean.ids.callCount
            assertCompleted(PersistenceLastOutcome.NewDocumentCreated, clean.workflow.pngImport.openAsNewWork())
            assertEquals(cleanIds + 1, clean.ids.callCount)
        }
    }

    @Test
    fun `autosave captures the opened work from its first committed change`() {
        runBlocking {
            val fixture = initializedFixture()
            setPendingNewWork(fixture)

            assertCompleted(PersistenceLastOutcome.NewDocumentCreated, fixture.workflow.pngImport.openAsNewWork())
            assertNull(fixture.workflow.autosave.value.pendingStateToken)

            apply(fixture.runtime, position(0, 1), black)
            assertNotNull(fixture.workflow.autosave.value.pendingStateToken)
        }
    }
}
