package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeUnderlayMemoryOperations
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryWrite
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayPublicationMode
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayPublicationStart
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayRecallStart
import kotlinx.coroutines.flow.StateFlow

/**
 * Recalls and publishes the installed work's underlay through [UnderlayMemoryPort] (ADR 0034).
 *
 * Nothing here takes the lease of a persistence operation or changes [PersistenceOperationProjection]: a memory
 * outcome is never shown to the user. Each call does nothing when [states] asks for nothing. A cancelled call
 * completes nothing, so the next request starts again.
 */
public class UnderlayMemoryWorkflow internal constructor(
    private val operations: RuntimeUnderlayMemoryOperations,
    private val port: UnderlayMemoryPort,
    public val states: StateFlow<UnderlayMemoryProjection>,
) {
    /** Reads the installed work's record and applies it, while [states] is `RecallPending`. */
    public suspend fun recall() {
        val start = operations.beginRecall()
        if (start is UnderlayRecallStart.Start) {
            operations.completeRecall(start, port.recall(start.document))
        }
    }

    /** Writes what [states] asks for; an underlay being adjusted is not written. */
    public suspend fun publish() {
        publishWith(UnderlayPublicationMode.Publish)
    }

    /** Writes what [states] asks for and also the value of an underlay being adjusted. */
    public suspend fun flush() {
        publishWith(UnderlayPublicationMode.Flush)
    }

    private suspend fun publishWith(mode: UnderlayPublicationMode) {
        val start = operations.beginPublication(mode)
        if (start is UnderlayPublicationStart.Start) {
            val publication = start.publication
            publication.writes.forEach { pending -> store(pending) }
            operations.completePublication(publication)
        }
    }

    private suspend fun store(write: UnderlayMemoryWrite) {
        when (write) {
            is UnderlayMemoryWrite.Remember -> port.remember(write.document, write.underlay)
            is UnderlayMemoryWrite.Forget -> port.forget(write.document)
        }
    }
}
