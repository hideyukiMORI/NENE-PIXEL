package io.github.hideyukimori.nenepixel.core.application.persistence

/**
 * What the device memory of the installed work's reference underlay is waiting for (ADR 0034,
 * Coordination). Each pending form carries an opaque [UnderlayMemoryToken]; a new token means the
 * pending request changed.
 */
public sealed interface UnderlayMemoryProjection {
    /** Nothing to read or write: the store is known and holds the workspace underlay. */
    public data object Settled : UnderlayMemoryProjection

    /** The store is unknown for the installed work; a recall is due. */
    public data class RecallPending internal constructor(
        public val token: UnderlayMemoryToken,
    ) : UnderlayMemoryProjection

    /** A departing capture, or a resting workspace underlay that differs from the store, is due. */
    public data class PublishPending internal constructor(
        public val token: UnderlayMemoryToken,
    ) : UnderlayMemoryProjection
}
