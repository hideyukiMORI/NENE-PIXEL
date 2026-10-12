package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.LegacySourceCopyOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectLoadOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectSaveOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStoragePort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRecordPort
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource

/** A fixture's project-file and recovery lifecycle, plus the palette choices for legacy conversion. */
internal data class TestDocumentPersistence(
    val storage: ProjectStoragePort = TestProjectStoragePort,
    val presets: LegacyPalettePresets? = null,
    val recovery: RecoveryRecordPort? = null,
)

private data object TestProjectStoragePort : ProjectStoragePort {
    override suspend fun save(document: DocumentState): ProjectSaveOutcome = ProjectSaveOutcome.Cancelled

    override suspend fun load(): ProjectLoadOutcome = ProjectLoadOutcome.Cancelled

    override suspend fun copyLegacySource(source: LegacyRgbaSource): LegacySourceCopyOutcome =
        LegacySourceCopyOutcome.Cancelled
}
