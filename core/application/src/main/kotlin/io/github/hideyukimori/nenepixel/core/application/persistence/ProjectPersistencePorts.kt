package io.github.hideyukimori.nenepixel.core.application.persistence

/** Explicit bindings for durable project storage and its private recovery record (ADR 0044). */
public data class ProjectPersistencePorts(
    public val storage: ProjectStoragePort,
    public val recovery: RecoveryRecordPort,
)
