package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGenerationResult

internal object RecoveryLineageMatcher {
    fun nextGeneration(
        expected: ExpectedRecoveryLineage,
        inspection: InternalInspection,
    ): RecoveryGenerationStep =
        when (inspection) {
            is InternalInspection.Failed -> {
                RecoveryGenerationStep.InspectionFailed
            }

            InternalInspection.Missing,
            is InternalInspection.Record,
            -> {
                matchedGeneration(expected, inspection)
            }
        }

    private fun matchedGeneration(
        expected: ExpectedRecoveryLineage,
        inspection: InternalInspection,
    ): RecoveryGenerationStep =
        if (matches(expected, inspection)) {
            successor(generationValue(inspection))
        } else {
            RecoveryGenerationStep.Stale
        }

    private fun successor(actualGeneration: Long): RecoveryGenerationStep =
        if (actualGeneration == Long.MAX_VALUE) {
            RecoveryGenerationStep.Exhausted
        } else {
            RecoveryGenerationStep.Next(createGeneration(actualGeneration + 1L))
        }

    private fun matches(
        expected: ExpectedRecoveryLineage,
        actual: InternalInspection,
    ): Boolean =
        when (expected) {
            ExpectedRecoveryLineage.Missing -> actual is InternalInspection.Missing
            is ExpectedRecoveryLineage.Present -> generationValue(actual) == expected.generation.value
        }

    private fun generationValue(inspection: InternalInspection): Long =
        when (inspection) {
            InternalInspection.Missing -> 0L
            is InternalInspection.Record -> inspection.record.generation.value
            is InternalInspection.Failed -> error("Failed inspection has no recovery generation")
        }

    private fun createGeneration(value: Long): RecoveryGeneration =
        when (val result = RecoveryGeneration.create(value)) {
            is RecoveryGenerationResult.Created -> result.generation
            RecoveryGenerationResult.Rejected -> error("Validated next recovery generation was rejected")
        }
}

internal sealed interface RecoveryGenerationStep {
    data class Next(
        val generation: RecoveryGeneration,
    ) : RecoveryGenerationStep

    data object InspectionFailed : RecoveryGenerationStep

    data object Stale : RecoveryGenerationStep

    data object Exhausted : RecoveryGenerationStep
}
