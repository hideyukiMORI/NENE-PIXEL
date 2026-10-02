package io.github.hideyukimori.nenepixel.measurement

import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryPort
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayRecollection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId

/** An underlay memory that remembers nothing (ADR 0034), for tests that do not look at the underlay. */
internal data object EmptyUnderlayMemoryPort : UnderlayMemoryPort {
    override suspend fun recall(document: DocumentId): UnderlayRecollection = UnderlayRecollection.Absent

    override suspend fun remember(
        document: DocumentId,
        underlay: RememberedUnderlay,
    ): UnderlayMemoryOutcome = UnderlayMemoryOutcome.Stored

    override suspend fun forget(document: DocumentId): UnderlayMemoryOutcome = UnderlayMemoryOutcome.Stored
}
