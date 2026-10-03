package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryPort
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayRecollection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers underlays in memory, one value per work (ADR 0034). Two test editors may share one instance, so an
 * underlay remembered by one runtime is recalled by another. Values come back as the same instance.
 */
internal class InMemoryUnderlayMemoryPort : UnderlayMemoryPort {
    private val store = ConcurrentHashMap<DocumentId, RememberedUnderlay>()

    /** What the store holds for [document] now. */
    fun stored(document: DocumentId): RememberedUnderlay? = store[document]

    override suspend fun recall(document: DocumentId): UnderlayRecollection =
        store[document]?.let(UnderlayRecollection::Remembered) ?: UnderlayRecollection.Absent

    override suspend fun remember(
        document: DocumentId,
        underlay: RememberedUnderlay,
    ): UnderlayMemoryOutcome {
        store[document] = underlay
        return UnderlayMemoryOutcome.Stored
    }

    override suspend fun forget(document: DocumentId): UnderlayMemoryOutcome {
        store.remove(document)
        return UnderlayMemoryOutcome.Stored
    }
}
