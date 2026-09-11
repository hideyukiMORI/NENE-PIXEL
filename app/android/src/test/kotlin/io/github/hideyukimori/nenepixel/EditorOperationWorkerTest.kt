package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceCancellationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportPort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class EditorOperationWorkerTest {
    @Test
    fun `real workflow export after publication cancels the retained worker`() =
        runBlocking {
            val recovery = BlockingRecoveryRecordPort()
            val started = CompletableDeferred<Unit>()
            val cleaned = CompletableDeferred<Unit>()
            val fixture =
                SchedulerFixture(
                    recovery,
                    PngExportPort {
                        try {
                            started.complete(Unit)
                            awaitCancellation()
                        } finally {
                            cleaned.complete(Unit)
                        }
                    },
                )
            fixture.initialize()
            fixture.applyNext()
            val publication = async(start = CoroutineStart.UNDISPATCHED) { fixture.workflow.publishLatestCapture() }
            recovery.awaitPublication()
            val worker = EditorOperationWorker(this)
            worker.launch { fixture.workflow.exportPng() }
            yield()
            recovery.completePublication()
            publication.await()
            started.await()
            val phase =
                assertInstanceOf(
                    PersistenceOperationPhase.Exporting::class.java,
                    fixture.workflow.operation.value.phase,
                )
            assertEquals(PersistenceCancellationResult.CancellationStarted, fixture.workflow.cancel(phase.operation))
            worker.cancel()
            cleaned.await()
            yield()
            assertEquals(PersistenceLastOutcome.Cancelled, fixture.workflow.operation.value.lastOutcome)
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
        }

    @Test
    fun `cancellation reaches user operation acquired after autosave wait`() =
        runBlocking {
            val worker = EditorOperationWorker(this)
            val autosave = CompletableDeferred<Unit>()
            val exportStarted = CompletableDeferred<Unit>()
            val exportCleaned = CompletableDeferred<Unit>()
            worker.launch {
                autosave.await()
                try {
                    exportStarted.complete(Unit)
                    awaitCancellation()
                } finally {
                    exportCleaned.complete(Unit)
                }
            }
            var duplicateRan = false
            worker.launch { duplicateRan = true }
            autosave.complete(Unit)
            exportStarted.await()
            worker.cancel()
            exportCleaned.await()
            yield()
            assertFalse(duplicateRan)
            val next = CompletableDeferred<Unit>()
            worker.launch { next.complete(Unit) }
            next.await()
            assertTrue(next.isCompleted)
        }

    @Test
    fun `cancellation before lazy job dispatch starts no operation`() =
        runBlocking {
            val worker = EditorOperationWorker(this)
            var ran = false
            worker.launch { ran = true }
            worker.cancel()
            yield()
            assertFalse(ran)
        }
}
