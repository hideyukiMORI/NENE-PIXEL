package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.editor.LegacyAdoptionPermit
import io.github.hideyukimori.nenepixel.core.application.editor.LegacyAdoptionStart
import io.github.hideyukimori.nenepixel.core.application.editor.LegacyCopyStart
import io.github.hideyukimori.nenepixel.core.application.editor.LegacyPreparationCompletion
import io.github.hideyukimori.nenepixel.core.application.editor.LegacyReductionStart
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeSwitchOperations
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.LegacyImportPlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class PersistenceLegacyImportFlow(
    private val operations: RuntimeSwitchOperations,
    private val ports: PersistencePorts,
    private val conversionDispatcher: CoroutineDispatcher,
) {
    private val commit: PersistenceSwitchCommitFlow =
        PersistenceSwitchCommitFlow(operations, ports.recoveryRecord)

    suspend fun copySource(operation: PersistenceOperationHandle): LegacySourceCopyRequestResult =
        when (val start = operations.legacy.beginCopy(operation)) {
            is LegacyCopyStart.Permit -> copySource(start)
            is LegacyCopyStart.Result -> start.result
        }

    private suspend fun copySource(start: LegacyCopyStart.Permit): LegacySourceCopyRequestResult =
        try {
            operations.legacy.completeCopy(
                start.permit,
                ports.projectStorage.copyLegacySource(start.permit.source),
            )
        } catch (cancelled: CancellationException) {
            operations.completeCancellation(start.permit.handle)
            throw cancelled
        }

    suspend fun previewSource(
        operation: PersistenceOperationHandle,
        destination: PaletteDefinition,
    ): LegacyReductionRequestResult =
        when (val start = operations.legacy.beginReduction(operation, destination)) {
            is LegacyReductionStart.Permit -> reduceSource(start)
            is LegacyReductionStart.Result -> start.result
        }

    private suspend fun reduceSource(start: LegacyReductionStart.Permit): LegacyReductionRequestResult =
        try {
            val preview =
                withContext(conversionDispatcher) {
                    LegacyImportPlanner.reduce(start.permit.candidate, start.permit.destination)
                }
            operations.legacy.completeReduction(start.permit, preview)
        } catch (cancelled: CancellationException) {
            operations.completeCancellation(start.permit.handle)
            throw cancelled
        }

    suspend fun acceptReduction(handle: LegacyReductionHandle): PersistenceRequestResult =
        when (val start = operations.legacy.beginAdoption(handle)) {
            is LegacyAdoptionStart.Permit -> prepareAndCommit(start.permit)
            is LegacyAdoptionStart.Confirmation -> PersistenceRequestResult.AwaitingConfirmation(start.request)
            is LegacyAdoptionStart.Result -> start.result
        }

    suspend fun prepareAndCommit(permit: LegacyAdoptionPermit): PersistenceRequestResult =
        try {
            when (
                val completion =
                    withContext(conversionDispatcher) {
                        operations.legacy.prepareAdoption(permit)
                    }
            ) {
                is LegacyPreparationCompletion.Ready -> commit.commit(completion.handle)
                is LegacyPreparationCompletion.Result -> completion.result
            }
        } catch (cancelled: CancellationException) {
            operations.completeCancellation(permit.handle)
            throw cancelled
        }
}
