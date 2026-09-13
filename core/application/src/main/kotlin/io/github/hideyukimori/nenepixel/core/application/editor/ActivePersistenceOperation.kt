package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPosition
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveStateToken
import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyCopyAttemptOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyReductionProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacySourcePreview
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceConfirmationRequest
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.LegacyImportResult
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.LegacyReductionPreview

internal sealed interface ActivePersistenceOperation {
    val handle: PersistenceOperationHandle

    data class Save(
        override val handle: PersistenceOperationHandle,
        val capture: SaveCapture,
        val phase: SavePhase,
    ) : ActivePersistenceOperation

    data class Export(
        override val handle: PersistenceOperationHandle,
        val runtimeGeneration: Long,
        val phase: ExportPhase,
    ) : ActivePersistenceOperation

    data class Autosave(
        override val handle: PersistenceOperationHandle,
        val capture: AutosaveCapture,
        val expected: ExpectedRecoveryLineage,
    ) : ActivePersistenceOperation

    data class RecoveryDecline(
        override val handle: PersistenceOperationHandle,
        val expected: ExpectedRecoveryLineage,
    ) : ActivePersistenceOperation

    sealed interface Switch : ActivePersistenceOperation {
        data class Loading(
            override val handle: PersistenceOperationHandle,
            val consentedSource: RuntimeSourceToken,
        ) : Switch

        data class Confirming(
            override val handle: PersistenceOperationHandle,
            val issuedSource: RuntimeSourceToken,
            val pending: PendingSwitch,
            val request: PersistenceConfirmationRequest,
        ) : Switch

        data class Ready(
            override val handle: PersistenceOperationHandle,
            val consentedSource: RuntimeSourceToken,
            val prepared: PreparedSwitch,
        ) : Switch {
            val candidate: RuntimeOwners
                get() = prepared.candidate

            val kind: SwitchKind
                get() = prepared.kind

            val verifiedLegacyRecovery: VerifiedLegacyRecoveryCommit?
                get() = prepared.verifiedLegacyRecovery
        }

        data class LegacyImport(
            val identity: LegacyImportOperationIdentity,
            val importSource: LegacyImportCandidate,
            val preservation: LegacyImportPreservation,
            val destinationState: LegacyImportDestination,
        ) : Switch {
            override val handle: PersistenceOperationHandle
                get() = identity.handle

            val consentedSource: RuntimeSourceToken
                get() = identity.consentedSource

            val sourceIdentity: LegacyImportSourceIdentity
                get() = identity.sourceIdentity

            val origin: LegacyImportSourceOrigin
                get() = identity.origin

            val candidate: LegacyImportResult.ConversionRequired
                get() = importSource.candidate

            val sourcePreview: LegacySourcePreview
                get() = importSource.preview

            val purpose: LegacyImportPurpose
                get() = preservation.purpose

            val originalCopyVerified: Boolean
                get() = preservation.originalCopyVerified

            val latestCopyOutcome: LegacyCopyAttemptOutcome
                get() = preservation.latestCopyOutcome

            val selectedDestination: PaletteDefinition?
                get() = destinationState.selected

            val destinationEpoch: Long
                get() = destinationState.epoch

            val phase: LegacyImportPhase
                get() = destinationState.phase

            fun withPhase(phase: LegacyImportPhase): LegacyImport =
                copy(destinationState = destinationState.copy(phase = phase))

            fun withCopyOutcome(
                verified: Boolean = originalCopyVerified,
                outcome: LegacyCopyAttemptOutcome,
                phase: LegacyImportPhase,
            ): LegacyImport =
                copy(
                    preservation =
                        preservation.copy(
                            originalCopyVerified = verified,
                            latestCopyOutcome = outcome,
                        ),
                    destinationState = destinationState.copy(phase = phase),
                )

            fun withDestination(
                selected: PaletteDefinition,
                epoch: Long,
                phase: LegacyImportPhase,
            ): LegacyImport = copy(destinationState = LegacyImportDestination(selected, epoch, phase))

            fun withConsentAndPhase(
                source: RuntimeSourceToken,
                phase: LegacyImportPhase,
            ): LegacyImport =
                copy(
                    identity = identity.copy(consentedSource = source),
                    destinationState = destinationState.copy(phase = phase),
                )
        }

        data class Switching(
            override val handle: PersistenceOperationHandle,
            val candidate: RuntimeOwners,
            val kind: SwitchKind,
        ) : Switch

        data class Cancelling(
            override val handle: PersistenceOperationHandle,
        ) : Switch
    }
}

internal enum class ExportPhase { Transport, Cancelling }

internal enum class SavePhase { Transport, Cleanup, Cancelling }

internal data class SaveCapture(
    val document: DocumentState,
    val historyPosition: HistoryPosition,
    val runtimeGeneration: Long,
    val recovery: SaveRecoveryCapture,
) {
    val stateToken: AutosaveStateToken
        get() = AutosaveStateToken(runtimeGeneration, historyPosition)
}

internal enum class SwitchKind { Loaded, NewDocument, LegacyImported }

internal data class PreparedSwitch(
    val candidate: RuntimeOwners,
    val kind: SwitchKind,
    val verifiedLegacyRecovery: VerifiedLegacyRecoveryCommit?,
)

internal data class LegacyImportOperationIdentity(
    val handle: PersistenceOperationHandle,
    val consentedSource: RuntimeSourceToken,
    val sourceIdentity: LegacyImportSourceIdentity,
    val origin: LegacyImportSourceOrigin,
)

internal data class LegacyImportCandidate(
    val candidate: LegacyImportResult.ConversionRequired,
    val preview: LegacySourcePreview,
)

internal data class LegacyImportPreservation(
    val purpose: LegacyImportPurpose,
    val originalCopyVerified: Boolean,
    val latestCopyOutcome: LegacyCopyAttemptOutcome,
)

internal data class LegacyImportDestination(
    val selected: PaletteDefinition?,
    val epoch: Long,
    val phase: LegacyImportPhase,
)

internal data class VerifiedLegacyRecoveryCommit(
    val generation: RecoveryGeneration,
    val source: LegacyRgbaSource,
)

internal class LegacyImportSourceIdentity

internal sealed interface LegacyImportSourceOrigin {
    data object UserFile : LegacyImportSourceOrigin

    data class Recovery(
        val generation: RecoveryGeneration,
    ) : LegacyImportSourceOrigin
}

internal enum class LegacyImportPurpose { Convert, DeclineRecovery }

internal sealed interface LegacyImportPhase {
    data class Required(
        val preview: BoundLegacyReductionPreview?,
    ) : LegacyImportPhase

    data class Copying(
        val previousPreview: BoundLegacyReductionPreview?,
    ) : LegacyImportPhase

    data class Reducing(
        val destination: PaletteDefinition,
        val epoch: Long,
    ) : LegacyImportPhase

    data class Preparing(
        val preview: BoundLegacyReductionPreview,
    ) : LegacyImportPhase

    data class Cancelling(
        val preview: BoundLegacyReductionPreview?,
    ) : LegacyImportPhase
}

internal data class BoundLegacyReductionPreview(
    val epoch: Long,
    val definition: PaletteDefinition,
    val preview: LegacyReductionPreview,
    val projection: LegacyReductionProjection,
)

internal sealed interface SwitchIntent {
    data object Load : SwitchIntent

    data class New(
        val request: NewDocumentRequest,
    ) : SwitchIntent
}

internal sealed interface PendingSwitch {
    data object Load : PendingSwitch

    data class New(
        val request: NewDocumentRequest,
    ) : PendingSwitch

    data class Prepared(
        val candidate: RuntimeOwners,
        val kind: SwitchKind,
        val verifiedLegacyRecovery: VerifiedLegacyRecoveryCommit?,
    ) : PendingSwitch

    data class Recover(
        val candidate: RuntimeOwners,
    ) : PendingSwitch

    data class LegacyPrepared(
        val operation: ActivePersistenceOperation.Switch.LegacyImport,
        val preview: BoundLegacyReductionPreview,
    ) : PendingSwitch
}

internal fun ActivePersistenceOperation?.isSwitching(): Boolean = this is ActivePersistenceOperation.Switch.Switching

internal fun ActivePersistenceOperation.isCancelling(): Boolean =
    (this is ActivePersistenceOperation.Save && phase == SavePhase.Cancelling) ||
        (this is ActivePersistenceOperation.Export && phase == ExportPhase.Cancelling) ||
        this is ActivePersistenceOperation.Switch.Cancelling ||
        (this is ActivePersistenceOperation.Switch.LegacyImport && phase is LegacyImportPhase.Cancelling)

internal fun ActivePersistenceOperation.isSaveCleanup(): Boolean =
    this is ActivePersistenceOperation.Save && phase == SavePhase.Cleanup

internal fun ActivePersistenceOperation.Save.toProjectionPhase(): PersistenceOperationPhase =
    if (phase == SavePhase.Cancelling) {
        PersistenceOperationPhase.Cancelling(handle)
    } else {
        PersistenceOperationPhase.Saving(handle)
    }

internal fun ActivePersistenceOperation.Switch.toProjectionPhase(): PersistenceOperationPhase =
    when (this) {
        is ActivePersistenceOperation.Switch.Loading,
        is ActivePersistenceOperation.Switch.Ready,
        -> {
            PersistenceOperationPhase.Loading(handle)
        }

        is ActivePersistenceOperation.Switch.LegacyImport -> {
            toProjectionPhase()
        }

        is ActivePersistenceOperation.Switch.Confirming -> {
            val legacy = pending as? PendingSwitch.LegacyPrepared
            if (legacy == null) {
                PersistenceOperationPhase.NeedsConfirmation(request)
            } else {
                PersistenceOperationPhase.NeedsLegacyConfirmation(request, legacy.operation.toProjection())
            }
        }

        is ActivePersistenceOperation.Switch.Switching -> {
            PersistenceOperationPhase.Switching(handle)
        }

        is ActivePersistenceOperation.Switch.Cancelling -> {
            PersistenceOperationPhase.Cancelling(handle)
        }
    }

private fun ActivePersistenceOperation.Switch.LegacyImport.toProjectionPhase(): PersistenceOperationPhase {
    val projection = toProjection()
    return when (phase) {
        is LegacyImportPhase.Required -> PersistenceOperationPhase.LegacyConversionRequired(projection)
        is LegacyImportPhase.Copying -> PersistenceOperationPhase.CopyingLegacySource(projection)
        is LegacyImportPhase.Reducing -> PersistenceOperationPhase.ReducingLegacySource(projection)
        is LegacyImportPhase.Preparing -> PersistenceOperationPhase.PreparingLegacyAdoption(projection)
        is LegacyImportPhase.Cancelling -> PersistenceOperationPhase.CancellingLegacyImport(projection)
    }
}

internal fun SwitchIntent.toPendingSwitch(): PendingSwitch =
    when (this) {
        SwitchIntent.Load -> PendingSwitch.Load
        is SwitchIntent.New -> PendingSwitch.New(request)
    }
