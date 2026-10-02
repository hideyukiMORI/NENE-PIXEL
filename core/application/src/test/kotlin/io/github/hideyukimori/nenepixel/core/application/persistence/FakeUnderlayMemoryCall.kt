package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId

/** One call that [FakeUnderlayMemoryPort] received, in the order it arrived. */
internal sealed interface FakeUnderlayMemoryCall {
    val document: DocumentId

    data class Recall(
        override val document: DocumentId,
    ) : FakeUnderlayMemoryCall

    data class Remember(
        override val document: DocumentId,
    ) : FakeUnderlayMemoryCall

    data class Forget(
        override val document: DocumentId,
    ) : FakeUnderlayMemoryCall
}
