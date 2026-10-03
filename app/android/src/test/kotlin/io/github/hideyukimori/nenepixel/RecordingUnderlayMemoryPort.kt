package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryPort
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayRecollection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import kotlinx.coroutines.CompletableDeferred

/** One port call seen by [RecordingUnderlayMemoryPort]. */
internal sealed interface RecordedUnderlayCall {
    data object Recall : RecordedUnderlayCall

    data class Remember(
        val underlay: RememberedUnderlay,
    ) : RecordedUnderlayCall

    data object Forget : RecordedUnderlayCall
}

/** Records the calls in order, the most calls ever running at once, and can hold a recall at a gate. */
internal class RecordingUnderlayMemoryPort : UnderlayMemoryPort {
    val calls: MutableList<RecordedUnderlayCall> = mutableListOf()
    var mostRunningAtOnce: Int = 0
        private set
    var recallGate: CompletableDeferred<Unit>? = null
    var failNextRemember: Boolean = false
    private var running: Int = 0

    override suspend fun recall(document: DocumentId): UnderlayRecollection =
        call(RecordedUnderlayCall.Recall) {
            recallGate?.await()
            UnderlayRecollection.Absent
        }

    override suspend fun remember(
        document: DocumentId,
        underlay: RememberedUnderlay,
    ): UnderlayMemoryOutcome =
        call(RecordedUnderlayCall.Remember(underlay)) {
            val outcome = if (failNextRemember) UnderlayMemoryOutcome.Failed else UnderlayMemoryOutcome.Stored
            failNextRemember = false
            outcome
        }

    override suspend fun forget(document: DocumentId): UnderlayMemoryOutcome =
        call(RecordedUnderlayCall.Forget) { UnderlayMemoryOutcome.Stored }

    private suspend fun <T> call(
        recorded: RecordedUnderlayCall,
        body: suspend () -> T,
    ): T {
        calls += recorded
        running += 1
        mostRunningAtOnce = maxOf(mostRunningAtOnce, running)
        try {
            return body()
        } finally {
            running -= 1
        }
    }
}
