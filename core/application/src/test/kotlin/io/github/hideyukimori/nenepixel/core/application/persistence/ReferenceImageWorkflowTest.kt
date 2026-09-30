package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayOpacity
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayPlacement
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

/** ADR 0032: the pick outcomes and what each leaves on the workspace. */
internal class ReferenceImageWorkflowTest {
    private val port = FakeReferenceImagePort()

    @Test
    fun `picked image becomes the underlay fitted to the current document without touching it`() =
        runBlocking {
            val fixture = initializedFixture()
            fixture.workflow.createNewDocument(newRequest(3, 2))
            val before = fixture.runtime.state
            val image = referenceImage(8, 4)
            port.handler = { ReferenceImageOutcome.Picked(image) }

            assertCompleted(PersistenceLastOutcome.ReferenceImagePicked, referenceImageWorkflow(fixture, port).pick())

            val after = fixture.runtime.state
            val underlay = after.workspaceState.underlay ?: fail("Underlay was not set")
            assertSame(image, underlay.image)
            assertEquals(canvas(3, 2), underlay.canvas)
            assertEquals(UnderlayPlacement.fitted(image, canvas(3, 2)), underlay.placement)
            assertEquals(ReferenceUnderlay.placed(image, canvas(3, 2)), underlay)
            assertSame(before.documentState, after.documentState)
            assertEquals(before.dirtyState, after.dirtyState)
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
            assertEquals(PersistenceLastOutcome.ReferenceImagePicked, fixture.workflow.operation.value.lastOutcome)
            assertEquals(1, port.calls)
        }

    @Test
    fun `a new pick replaces the existing underlay with a freshly placed one`() =
        runBlocking {
            val fixture = initializedFixture()
            val old =
                ReferenceUnderlay
                    .placed(referenceImage(2, 2), canvas(4, 4))
                    .withOpacity(UnderlayOpacity.create(10))
            fixture.runtime.reduce(WorkspaceAction.SetReferenceUnderlay(old))
            val image = referenceImage(4, 8)
            port.handler = { ReferenceImageOutcome.Picked(image) }

            assertCompleted(PersistenceLastOutcome.ReferenceImagePicked, referenceImageWorkflow(fixture, port).pick())

            assertEquals(ReferenceUnderlay.placed(image, canvas(4, 4)), fixture.runtime.state.workspaceState.underlay)
        }

    @Test
    fun `cancelled pick completes as cancelled and leaves the state alone`() =
        runBlocking {
            val fixture = initializedFixture()
            val before = fixture.runtime.state
            port.handler = { ReferenceImageOutcome.Cancelled }

            assertCompleted(PersistenceLastOutcome.Cancelled, referenceImageWorkflow(fixture, port).pick())

            assertEquals(before, fixture.runtime.state)
            assertNull(fixture.runtime.state.workspaceState.underlay)
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
        }

    @Test
    fun `each rejection is typed with its reason and keeps the existing underlay`() =
        runBlocking {
            val fixture = initializedFixture()
            val existing = ReferenceUnderlay.placed(referenceImage(2, 2), canvas(4, 4))
            fixture.runtime.reduce(WorkspaceAction.SetReferenceUnderlay(existing))
            val workflow = referenceImageWorkflow(fixture, port)
            val reasons =
                listOf(
                    ReferenceImageSourceRejection.TooManyBytes,
                    ReferenceImageSourceRejection.TooManyPixels,
                    ReferenceImageSourceRejection.Unsupported,
                )

            val failures =
                reasons.map { reason ->
                    port.handler = { ReferenceImageOutcome.Rejected(reason) }
                    assertFailed(workflow.pick())
                }

            assertEquals(reasons.map { PersistenceFailure.ReferenceImageRejected(it) }, failures)
            assertEquals(existing, fixture.runtime.state.workspaceState.underlay)
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
        }

    @Test
    fun `failed pick is typed as a reference image pick failure`() =
        runBlocking {
            val fixture = initializedFixture()
            val before = fixture.runtime.state
            port.handler = { ReferenceImageOutcome.Failed(ProjectStorageFailure.InvalidPickerResult) }

            val failure =
                assertInstanceOf(
                    PersistenceFailure.ReferenceImagePick::class.java,
                    assertFailed(referenceImageWorkflow(fixture, port).pick()),
                )

            assertEquals(ProjectStorageFailure.InvalidPickerResult, failure.failure)
            assertEquals(before, fixture.runtime.state)
        }
}
