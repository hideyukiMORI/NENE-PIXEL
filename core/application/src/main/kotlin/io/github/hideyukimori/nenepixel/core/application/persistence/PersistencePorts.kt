package io.github.hideyukimori.nenepixel.core.application.persistence

/** The persistence capabilities consumed by the one editor workflow. */
public data class PersistencePorts(
    public val project: ProjectPersistencePorts,
    public val png: PngPersistencePorts,
    public val palette: PalettePersistencePorts,
    public val underlay: UnderlayPersistencePorts,
) {
    public val projectStorage: ProjectStoragePort get() = project.storage
    public val recoveryRecord: RecoveryRecordPort get() = project.recovery
    public val pngExport: PngExportPort get() = png.exporter
    public val pngImport: PngImportPort get() = png.importer
    public val paletteJsonExport: PaletteJsonExportPort get() = palette.exporter
    public val paletteJsonImport: PaletteJsonImportPort get() = palette.importer
    public val referenceImage: ReferenceImagePort get() = underlay.referenceImage
    public val underlayMemory: UnderlayMemoryPort get() = underlay.memory
}
