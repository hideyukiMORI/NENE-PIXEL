package io.github.hideyukimori.nenepixel.core.application.persistence

/** The closed outcome of [UnderlayMemoryPort.remember] and [UnderlayMemoryPort.forget] (ADR 0034). */
public enum class UnderlayMemoryOutcome {
    /** The store holds the requested state; for [UnderlayMemoryPort.forget], no record. */
    Stored,

    /** The store could not be brought to the requested state. */
    Failed,
}
