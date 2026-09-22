package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition

public class LegacyImportWorkflow internal constructor(
    private val switching: PersistenceSwitchFlow,
    private val recovery: PersistenceRecoveryFlow,
) {
    public suspend fun copySource(operation: PersistenceOperationHandle): LegacySourceCopyRequestResult =
        switching.legacy.copySource(operation)

    public suspend fun previewSource(
        operation: PersistenceOperationHandle,
        destination: PaletteDefinition,
    ): LegacyReductionRequestResult = switching.legacy.previewSource(operation, destination)

    public suspend fun acceptReduction(handle: LegacyReductionHandle): PersistenceRequestResult =
        switching.legacy.acceptReduction(handle)

    public suspend fun declineRecovery(operation: PersistenceOperationHandle): PersistenceRequestResult =
        recovery.declineLegacyRecovery(operation)
}
