package io.github.hideyukimori.nenepixel.core.application.persistence

/** Explicit bindings for reference acquisition and isolated per-work underlay memory (ADR 0044). */
public data class UnderlayPersistencePorts(
    public val referenceImage: ReferenceImagePort,
    public val memory: UnderlayMemoryPort,
)
