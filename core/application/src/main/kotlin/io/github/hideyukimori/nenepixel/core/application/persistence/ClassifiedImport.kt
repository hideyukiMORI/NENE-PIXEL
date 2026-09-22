package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.LegacyImportPlanner
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.LegacyImportResult

internal sealed interface ClassifiedImport {
    data class Current(
        val document: DocumentState,
    ) : ClassifiedImport

    data class Legacy(
        val candidate: LegacyImportResult.ConversionRequired,
    ) : ClassifiedImport
}

internal fun classifyImport(source: DocumentImportSource): ClassifiedImport =
    when (source) {
        is DocumentImportSource.Current -> {
            ClassifiedImport.Current(source.document)
        }

        is DocumentImportSource.Legacy -> {
            when (val classified = LegacyImportPlanner.classify(source.source)) {
                is LegacyImportResult.Lossless -> ClassifiedImport.Current(classified.document)
                is LegacyImportResult.ConversionRequired -> ClassifiedImport.Legacy(classified)
            }
        }
    }

internal sealed interface ClassifiedProjectLoadOutcome {
    data class Loaded(
        val source: ClassifiedImport,
    ) : ClassifiedProjectLoadOutcome

    data object Cancelled : ClassifiedProjectLoadOutcome

    data class Failed(
        val failure: ProjectStorageFailure,
    ) : ClassifiedProjectLoadOutcome
}

internal fun ProjectLoadOutcome.classify(): ClassifiedProjectLoadOutcome =
    when (this) {
        is ProjectLoadOutcome.Loaded -> ClassifiedProjectLoadOutcome.Loaded(classifyImport(source))
        ProjectLoadOutcome.Cancelled -> ClassifiedProjectLoadOutcome.Cancelled
        is ProjectLoadOutcome.Failed -> ClassifiedProjectLoadOutcome.Failed(failure)
    }

internal sealed interface ClassifiedRecoveryInspection {
    data object Missing : ClassifiedRecoveryInspection

    data class Retired(
        val generation: RecoveryGeneration,
    ) : ClassifiedRecoveryInspection

    data class Candidate(
        val generation: RecoveryGeneration,
        val source: ClassifiedImport,
    ) : ClassifiedRecoveryInspection

    data class Failed(
        val failure: RecoveryInspectionFailure,
    ) : ClassifiedRecoveryInspection
}

internal fun RecoveryInspection.classify(): ClassifiedRecoveryInspection =
    when (this) {
        RecoveryInspection.Missing -> ClassifiedRecoveryInspection.Missing
        is RecoveryInspection.Retired -> ClassifiedRecoveryInspection.Retired(generation)
        is RecoveryInspection.Candidate -> ClassifiedRecoveryInspection.Candidate(generation, classifyImport(source))
        is RecoveryInspection.Failed -> ClassifiedRecoveryInspection.Failed(failure)
    }
