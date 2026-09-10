package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGenerationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryPublicationOutcome
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class AutosaveTransitionsTest {
    @Test
    fun `a completion from an abandoned runtime is stale and publishes nothing`() {
        val started = AutosaveTransitions.begin(readyCoordination(document))
        val handle = assertInstanceOf(AutosaveStart.Started::class.java, started.result).handle
        val switched =
            started.next
                .advanceRuntimeGeneration()
                .withActive(null)
                .let { it.withAutosave(AutosaveTracking.initial(it.runtimeGeneration)) }

        val completion =
            AutosaveTransitions.complete(
                switched,
                handle,
                RecoveryPublicationOutcome.Published(generation(2)),
            )

        assertEquals(AutosaveRequestResult.Stale, completion.result)
        assertSame(switched, completion.next)
        assertNull(completion.next.autosave.publishedRevision)
        assertEquals(AutosaveLastOutcome.None, completion.next.autosave.lastOutcome)
    }

    @Test
    fun `a capture recorded by a replaced runtime is dropped instead of published`() {
        val replaced =
            readyCoordination(document)
                .advanceRuntimeGeneration()
                .let { it.withAutosave(it.autosave.copy(publishedGeneration = it.runtimeGeneration)) }

        val started = AutosaveTransitions.begin(replaced)

        assertEquals(AutosaveStart.NoCapture, started.result)
        assertNull(started.next.autosave.pending)
    }

    @Test
    fun `an active operation defers the request and keeps the capture`() {
        val ready = readyCoordination(document)
        val busy = AutosaveTransitions.begin(ready).next

        val deferred = AutosaveTransitions.begin(busy)

        val pending = deferred.next.autosave.pending
        assertEquals(AutosaveStart.Deferred, deferred.result)
        assertEquals(document.revision.value, pending?.revision)
        assertEquals(AutosaveLastOutcome.Deferred, deferred.next.autosave.lastOutcome)
    }
}

private val document: DocumentState = state(canvas(2, 2))

private fun readyCoordination(document: DocumentState): PersistenceCoordination =
    AutosaveTransitions.recordCapture(
        PersistenceCoordination.initial().withRecovery(
            RuntimeRecoveryState.Clear(ExpectedRecoveryLineage.Missing),
        ),
        document,
    )

private fun generation(value: Long): RecoveryGeneration =
    when (val result = RecoveryGeneration.create(value)) {
        is RecoveryGenerationResult.Created -> result.generation
        RecoveryGenerationResult.Rejected -> fail("Recovery generation fixture was rejected: $value")
    }
