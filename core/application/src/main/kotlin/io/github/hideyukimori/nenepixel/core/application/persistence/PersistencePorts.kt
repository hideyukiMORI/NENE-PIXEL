package io.github.hideyukimori.nenepixel.core.application.persistence

/** The persistence capabilities consumed by the one editor workflow. */
public data class PersistencePorts(
    public val projectStorage: ProjectStoragePort,
    public val recoveryRecord: RecoveryRecordPort,
    public val pngExport: PngExportPort,
    public val paletteJsonExport: PaletteJsonExportPort,
)
