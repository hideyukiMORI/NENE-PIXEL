package io.github.hideyukimori.nenepixel.core.application.persistence

/** Explicit bindings for the existing palette JSON interchange capabilities (ADR 0044). */
public data class PalettePersistencePorts(
    public val exporter: PaletteJsonExportPort,
    public val importer: PaletteJsonImportPort,
)
