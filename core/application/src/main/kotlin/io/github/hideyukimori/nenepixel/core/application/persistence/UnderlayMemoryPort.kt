package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId

/**
 * Keeps one reference underlay per work on this device (ADR 0034).
 *
 * No function throws for an expected failure. [recall] has no failure outcome: an unreadable record and an I/O
 * failure are both answered as [UnderlayRecollection.Absent]. [remember] and [forget] report [UnderlayMemoryOutcome],
 * which is never projected to the user. The port is called outside the lease of a persistence operation.
 */
public interface UnderlayMemoryPort {
    /** Reads what this device remembers for [document], or [UnderlayRecollection.Absent]. */
    public suspend fun recall(document: DocumentId): UnderlayRecollection

    /** Replaces what this device remembers for [document] with [underlay]. */
    public suspend fun remember(
        document: DocumentId,
        underlay: RememberedUnderlay,
    ): UnderlayMemoryOutcome

    /** Removes what this device remembers for [document]; a missing record is already the requested state. */
    public suspend fun forget(document: DocumentId): UnderlayMemoryOutcome
}
