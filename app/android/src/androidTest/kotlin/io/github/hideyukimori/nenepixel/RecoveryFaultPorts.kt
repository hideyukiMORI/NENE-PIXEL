package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.application.persistence.LegacySourceCopyOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PalettePersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportCapture
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PngPersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectLoadOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectPersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectSaveOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStoragePort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRecordPort
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImageOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImagePort
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayPersistencePorts
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource
import io.github.hideyukimori.nenepixel.measurement.EmptyUnderlayMemoryPort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

internal class RecoveryFaultPorts(
    document: DocumentState,
    recovery: RecoveryRecordPort,
) {
    val export = RecoveryFaultExportPort()
    val ports =
        PersistencePorts(
            ProjectPersistencePorts(RecoveryFaultLoadPort(document), recovery),
            PngPersistencePorts(export, PngImportPort { PngImportOutcome.Cancelled }),
            PalettePersistencePorts(
                PaletteJsonExportPort { PaletteJsonExportOutcome.Cancelled },
                PaletteJsonImportPort { PaletteJsonImportOutcome.Cancelled },
            ),
            UnderlayPersistencePorts(ReferenceImagePort { ReferenceImageOutcome.Cancelled }, EmptyUnderlayMemoryPort),
        )
}

/** Held user operation changes the real runtime gate; it never fabricates a recovery result. */
internal class RecoveryFaultExportPort : PngExportPort {
    val entered = CompletableDeferred<Unit>()
    private val released = CompletableDeferred<Unit>()

    override suspend fun export(capture: PngExportCapture): PngExportOutcome {
        entered.complete(Unit)
        withTimeout(10_000L) { released.await() }
        return PngExportOutcome.Cancelled
    }

    fun release() {
        released.complete(Unit)
    }
}

private class RecoveryFaultLoadPort(
    private val document: DocumentState,
) : ProjectStoragePort {
    override suspend fun load(): ProjectLoadOutcome = ProjectLoadOutcome.Loaded(DocumentImportSource.Current(document))

    override suspend fun save(document: DocumentState): ProjectSaveOutcome = ProjectSaveOutcome.Cancelled

    override suspend fun copyLegacySource(source: LegacyRgbaSource): LegacySourceCopyOutcome =
        error("Synthetic recovery tests do not copy legacy sources")
}
