package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId

/**
 * Remembers underlays in memory, keyed by work. Values are kept and returned as the same instance.
 *
 * Not thread-safe: tests call it from one coroutine context.
 */
internal class FakeUnderlayMemoryPort : UnderlayMemoryPort {
    private val store = mutableMapOf<DocumentId, RememberedUnderlay>()
    private val received = mutableListOf<FakeUnderlayMemoryCall>()
    private var nextRecallGate: FakeUnderlayRecallGate? = null

    /** A snapshot of every call so far, in order; later calls do not change it. */
    val calls: List<FakeUnderlayMemoryCall>
        get() = received.toList()

    /** When true, the next [remember] or [forget] answers Failed without changing the store, then resets. */
    var failNextWrite: Boolean = false

    /** What the store holds for [document] now, without recording a call. */
    fun stored(document: DocumentId): RememberedUnderlay? = store[document]

    /** Puts [underlay] in the store without recording a call. */
    fun seed(
        document: DocumentId,
        underlay: RememberedUnderlay,
    ) {
        store[document] = underlay
    }

    /** Holds the next [recall] after it is recorded; it reads the store only after the gate is released. */
    fun holdNextRecall(): FakeUnderlayRecallGate {
        val gate = FakeUnderlayRecallGate()
        nextRecallGate = gate
        return gate
    }

    override suspend fun recall(document: DocumentId): UnderlayRecollection {
        received += FakeUnderlayMemoryCall.Recall(document)
        val gate = nextRecallGate
        nextRecallGate = null
        gate?.pass()
        val underlay = store[document] ?: return UnderlayRecollection.Absent
        return UnderlayRecollection.Remembered(underlay)
    }

    override suspend fun remember(
        document: DocumentId,
        underlay: RememberedUnderlay,
    ): UnderlayMemoryOutcome {
        received += FakeUnderlayMemoryCall.Remember(document)
        if (consumeFailure()) {
            return UnderlayMemoryOutcome.Failed
        }
        store[document] = underlay
        return UnderlayMemoryOutcome.Stored
    }

    override suspend fun forget(document: DocumentId): UnderlayMemoryOutcome {
        received += FakeUnderlayMemoryCall.Forget(document)
        if (consumeFailure()) {
            return UnderlayMemoryOutcome.Failed
        }
        store.remove(document)
        return UnderlayMemoryOutcome.Stored
    }

    private fun consumeFailure(): Boolean {
        val fail = failNextWrite
        failNextWrite = false
        return fail
    }
}
