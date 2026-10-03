package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId

/** One write of a publication (ADR 0034): remember a value for a work, or forget the work's record. */
internal sealed interface UnderlayMemoryWrite {
    val document: DocumentId

    /** The value the store holds for [document] once this write is stored; null after a forget. */
    val remembered: RememberedUnderlay?

    data class Remember(
        override val document: DocumentId,
        val underlay: RememberedUnderlay,
    ) : UnderlayMemoryWrite {
        override val remembered: RememberedUnderlay
            get() = underlay
    }

    data class Forget(
        override val document: DocumentId,
    ) : UnderlayMemoryWrite {
        override val remembered: RememberedUnderlay?
            get() = null
    }

    companion object {
        fun of(
            document: DocumentId,
            remembered: RememberedUnderlay?,
        ): UnderlayMemoryWrite = remembered?.let { underlay -> Remember(document, underlay) } ?: Forget(document)
    }
}
