package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyImportDestinationProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyImportOrigin
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyImportPreservationProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyImportProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyImportSourceProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyOriginalCopyStatus

internal fun ActivePersistenceOperation.Switch.LegacyImport.toProjection(): LegacyImportProjection {
    val bound =
        (phase as? LegacyImportPhase.Required)?.preview
            ?: (phase as? LegacyImportPhase.Copying)?.previousPreview
            ?: (phase as? LegacyImportPhase.Preparing)?.preview
            ?: (phase as? LegacyImportPhase.Cancelling)?.preview
    return LegacyImportProjection(
        operation = handle,
        source =
            LegacyImportSourceProjection(
                sourcePreview,
                candidate.distinctColorCount,
                when (origin) {
                    LegacyImportSourceOrigin.UserFile -> LegacyImportOrigin.USER_FILE
                    is LegacyImportSourceOrigin.Recovery -> LegacyImportOrigin.RECOVERY
                },
            ),
        preservation =
            LegacyImportPreservationProjection(
                when {
                    originalCopyVerified -> LegacyOriginalCopyStatus.VERIFIED
                    origin is LegacyImportSourceOrigin.Recovery -> LegacyOriginalCopyStatus.REQUIRED
                    else -> LegacyOriginalCopyStatus.OPTIONAL
                },
                latestCopyOutcome,
            ),
        destination = LegacyImportDestinationProjection(selectedDestination, bound?.projection),
    )
}
