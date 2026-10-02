package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.LegacySourceCopyOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectLoadOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectSaveOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStoragePort
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource

/**
 * Project storage that keeps the last saved document and returns it on the next load, so a work saved by one test
 * editor can be loaded, with the same id, into another. A load before any save is cancelled.
 */
internal class SavedWorkStoragePort : ProjectStoragePort {
    @Volatile
    var saved: DocumentState? = null
        private set

    override suspend fun save(document: DocumentState): ProjectSaveOutcome {
        saved = document
        return ProjectSaveOutcome.Saved
    }

    override suspend fun load(): ProjectLoadOutcome =
        saved?.let { document -> ProjectLoadOutcome.Loaded(DocumentImportSource.Current(document)) }
            ?: ProjectLoadOutcome.Cancelled

    override suspend fun copyLegacySource(source: LegacyRgbaSource): LegacySourceCopyOutcome =
        LegacySourceCopyOutcome.Cancelled
}
