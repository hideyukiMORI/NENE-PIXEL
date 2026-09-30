package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.editor.ReferenceImagePickStart
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** ADR 0032: the pick shares the document-output lease, so busy, stale and cancel follow it. */
internal class ReferenceImageWorkflowLeaseTest {
    private val port = FakeReferenceImagePort()
    private val image = referenceImage(8, 4)

    @Test
    fun `pick is busy while another operation holds the lease and never asks the port`() =
        runBlocking {
            val fixture = initializedFixture()
            val gate = CompletableDeferred<Unit>()
            fixture.exporter.handler = {
                gate.await()
                PngExportOutcome.Exported
            }
            val export = async(start = CoroutineStart.UNDISPATCHED) { fixture.workflow.exportPng() }

            assertEquals(PersistenceRequestResult.Busy, referenceImageWorkflow(fixture, port).pick())

            assertEquals(0, port.calls)
            gate.complete(Unit)
            assertCompleted(PersistenceLastOutcome.PngExported, export.await())
            assertNull(fixture.runtime.state.workspaceState.underlay)
        }

    @Test
    fun `other IO stays busy while the pick is in flight`() =
        runBlocking {
            val fixture = initializedFixture()
            val gate = CompletableDeferred<Unit>()
            port.handler = {
                gate.await()
                ReferenceImageOutcome.Picked(image)
            }
            val workflow = referenceImageWorkflow(fixture, port)
            val pending = async(start = CoroutineStart.UNDISPATCHED) { workflow.pick() }

            assertInstanceOf(PersistenceOperationPhase.Exporting::class.java, fixture.workflow.operation.value.phase)
            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.saveAs())
            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.load())
            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.createNewDocument(newRequest(3, 2)))
            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.exportPng())
            assertEquals(PersistenceRequestResult.Busy, workflow.pick())
            gate.complete(Unit)

            assertCompleted(PersistenceLastOutcome.ReferenceImagePicked, pending.await())
            assertEquals(1, port.calls)
            assertEquals(ReferenceUnderlay.placed(image, canvas(4, 4)), fixture.runtime.state.workspaceState.underlay)
        }

    @Test
    fun `a pick completed after the document was replaced is stale and sets no underlay`() =
        runBlocking {
            val fixture = initializedFixture()
            val operations = fixture.runtime.referenceImageOperations
            val first = assertInstanceOf(ReferenceImagePickStart.Started::class.java, operations.beginPick())
            operations.cancelPick(first.handle)
            fixture.workflow.createNewDocument(newRequest(3, 2))
            assertEquals(canvas(3, 2), fixture.runtime.state.documentState.size)
            val projection = fixture.workflow.operation.value
            val state = fixture.runtime.state

            assertEquals(
                PersistenceRequestResult.Stale,
                operations.completePick(first.handle, ReferenceImageOutcome.Picked(image)),
            )

            assertEquals(projection, fixture.workflow.operation.value)
            assertEquals(state, fixture.runtime.state)
            assertNull(fixture.runtime.state.workspaceState.underlay)
        }

    @Test
    fun `a stale completion neither clears a newer pick nor sets the underlay`() =
        runBlocking {
            val fixture = initializedFixture()
            val operations = fixture.runtime.referenceImageOperations
            val first = assertInstanceOf(ReferenceImagePickStart.Started::class.java, operations.beginPick())
            operations.completePick(first.handle, ReferenceImageOutcome.Cancelled)
            val second = assertInstanceOf(ReferenceImagePickStart.Started::class.java, operations.beginPick())
            val projection = fixture.workflow.operation.value

            assertEquals(
                PersistenceRequestResult.Stale,
                operations.completePick(first.handle, ReferenceImageOutcome.Picked(image)),
            )

            assertEquals(projection, fixture.workflow.operation.value)
            assertNull(fixture.runtime.state.workspaceState.underlay)
            assertCompleted(
                PersistenceLastOutcome.ReferenceImagePicked,
                operations.completePick(second.handle, ReferenceImageOutcome.Picked(image)),
            )
            assertEquals(ReferenceUnderlay.placed(image, canvas(4, 4)), fixture.runtime.state.workspaceState.underlay)
        }

    @Test
    fun `cancelling the operation during the pick drops the picked image`() =
        runBlocking {
            val fixture = initializedFixture()
            val gate = CompletableDeferred<Unit>()
            port.handler = {
                gate.await()
                ReferenceImageOutcome.Picked(image)
            }
            val pending = async(start = CoroutineStart.UNDISPATCHED) { referenceImageWorkflow(fixture, port).pick() }
            val phase =
                assertInstanceOf(
                    PersistenceOperationPhase.Exporting::class.java,
                    fixture.workflow.operation.value.phase,
                )

            assertEquals(PersistenceCancellationResult.CancellationStarted, fixture.workflow.cancel(phase.operation))
            gate.complete(Unit)

            assertCompleted(PersistenceLastOutcome.Cancelled, pending.await())
            assertNull(fixture.runtime.state.workspaceState.underlay)
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
        }

    @Test
    fun `coroutine cancellation releases the lease without an underlay`() =
        runBlocking {
            val fixture = initializedFixture()
            port.handler = {
                CompletableDeferred<Unit>().await()
                ReferenceImageOutcome.Picked(image)
            }
            val pending = async(start = CoroutineStart.UNDISPATCHED) { referenceImageWorkflow(fixture, port).pick() }

            pending.cancelAndJoin()

            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
            assertEquals(PersistenceLastOutcome.Cancelled, fixture.workflow.operation.value.lastOutcome)
            assertNull(fixture.runtime.state.workspaceState.underlay)
            port.handler = { ReferenceImageOutcome.Picked(image) }
            assertCompleted(PersistenceLastOutcome.ReferenceImagePicked, referenceImageWorkflow(fixture, port).pick())
        }
}
