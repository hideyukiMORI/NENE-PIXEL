package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyReductionHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyReductionRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacySourceCopyOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacySourceCopyRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.LegacyReductionPreview

internal class RuntimeLegacyImportOperations(
    private val runtime: EditorRuntime,
) {
    fun beginCopy(handle: PersistenceOperationHandle): LegacyCopyStart =
        runtime.transact { transaction -> LegacyImportTransitions.beginCopy(transaction.coordination, handle) }

    fun completeCopy(
        permit: LegacyCopyPermit,
        outcome: LegacySourceCopyOutcome,
    ): LegacySourceCopyRequestResult =
        runtime.transact { transaction ->
            LegacyImportTransitions.completeCopy(transaction.coordination, permit, outcome)
        }

    fun beginReduction(
        handle: PersistenceOperationHandle,
        destination: PaletteDefinition,
    ): LegacyReductionStart =
        runtime.transact { transaction ->
            LegacyImportTransitions.beginReduction(transaction.coordination, handle, destination)
        }

    fun completeReduction(
        permit: LegacyReductionPermit,
        preview: LegacyReductionPreview,
    ): LegacyReductionRequestResult =
        runtime.transact { transaction ->
            LegacyImportTransitions.completeReduction(transaction.coordination, permit, preview)
        }

    fun beginAdoption(handle: LegacyReductionHandle): LegacyAdoptionStart =
        runtime.transact { transaction ->
            LegacyImportTransitions.beginAdoption(transaction.coordination, handle, transaction.switchContext())
        }

    fun prepareAdoption(permit: LegacyAdoptionPermit): LegacyPreparationCompletion {
        val owners = permit.prepareOwners()
        return runtime.transact { transaction ->
            LegacyImportTransitions.completeAdoptionPreparation(transaction.coordination, permit, owners)
        }
    }
}
