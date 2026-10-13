package io.github.hideyukimori.nenepixel.core.application.persistence

/** Explicit bindings for the existing PNG interchange capabilities (ADR 0044). */
public data class PngPersistencePorts(
    public val exporter: PngExportPort,
    public val importer: PngImportPort,
)
