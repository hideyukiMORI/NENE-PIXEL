package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.presentation.compose.R

internal fun PersistenceOperationPhase.LegacyImport.conversionStatusResource(): Int =
    when (this) {
        is PersistenceOperationPhase.LegacyConversionRequired -> R.string.legacy_waiting
        is PersistenceOperationPhase.CopyingLegacySource -> R.string.legacy_copying
        is PersistenceOperationPhase.ReducingLegacySource -> R.string.legacy_reducing
        is PersistenceOperationPhase.PreparingLegacyAdoption -> R.string.legacy_preparing
        is PersistenceOperationPhase.NeedsLegacyConfirmation -> R.string.waiting_confirmation
        is PersistenceOperationPhase.CancellingLegacyImport -> R.string.cancelling_operation
    }
