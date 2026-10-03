package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryPort
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayRecollection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId

/** An underlay memory that remembers nothing: every recall is absent and every write is stored (ADR 0034). */
internal data object EmptyUnderlayMemoryPort : UnderlayMemoryPort {
    override suspend fun recall(document: DocumentId): UnderlayRecollection = UnderlayRecollection.Absent

    override suspend fun remember(
        document: DocumentId,
        underlay: RememberedUnderlay,
    ): UnderlayMemoryOutcome = UnderlayMemoryOutcome.Stored

    override suspend fun forget(document: DocumentId): UnderlayMemoryOutcome = UnderlayMemoryOutcome.Stored
}
