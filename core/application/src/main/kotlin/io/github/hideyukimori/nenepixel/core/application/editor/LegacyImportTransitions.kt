package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyCopyAttemptOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyReductionHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyReductionProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyReductionRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacySourceCopyOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacySourceCopyRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacySourcePreview
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.LegacyImportResult
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.LegacyReductionPreview

internal data class LegacyCopyPermit(
    val handle: PersistenceOperationHandle,
    val sourceIdentity: LegacyImportSourceIdentity,
    val source: LegacyRgbaSource,
)

internal sealed interface LegacyCopyStart {
    data class Permit(
        val permit: LegacyCopyPermit,
    ) : LegacyCopyStart

    data class Result(
        val result: LegacySourceCopyRequestResult,
    ) : LegacyCopyStart
}

internal data class LegacyReductionPermit(
    val operation: LegacyOperationProof,
    val candidate: LegacyImportResult.ConversionRequired,
    val destinationBinding: LegacyDestinationBinding,
) {
    val handle: PersistenceOperationHandle
        get() = operation.handle

    val sourceIdentity: LegacyImportSourceIdentity
        get() = operation.sourceIdentity

    val epoch: Long
        get() = destinationBinding.epoch

    val destination: PaletteDefinition
        get() = destinationBinding.destination
}

internal data class LegacyOperationProof(
    val handle: PersistenceOperationHandle,
    val sourceIdentity: LegacyImportSourceIdentity,
)

internal data class LegacyDestinationBinding(
    val epoch: Long,
    val destination: PaletteDefinition,
)

internal sealed interface LegacyReductionStart {
    data class Permit(
        val permit: LegacyReductionPermit,
    ) : LegacyReductionStart

    data class Result(
        val result: LegacyReductionRequestResult,
    ) : LegacyReductionStart
}

internal data class LegacyAdoptionPermit(
    val handle: PersistenceOperationHandle,
    val sourceIdentity: LegacyImportSourceIdentity,
    val epoch: Long,
    val prepareOwners: () -> RuntimeOwners,
)

internal sealed interface LegacyAdoptionStart {
    data class Permit(
        val permit: LegacyAdoptionPermit,
    ) : LegacyAdoptionStart

    data class Confirmation(
        val request: io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceConfirmationRequest,
    ) : LegacyAdoptionStart

    data class Result(
        val result: PersistenceRequestResult,
    ) : LegacyAdoptionStart
}

internal sealed interface LegacyPreparationCompletion {
    data class Ready(
        val handle: PersistenceOperationHandle,
    ) : LegacyPreparationCompletion

    data class Result(
        val result: PersistenceRequestResult,
    ) : LegacyPreparationCompletion
}

internal object LegacyImportTransitions {
    fun startRecovery(
        coordination: PersistenceCoordination,
        context: SwitchContext,
        purpose: LegacyImportPurpose,
    ): PersistenceTransition<PersistenceRequestResult> {
        val recovery = coordination.recoveryState as? RuntimeRecoveryState.LegacyCandidate
        return when {
            coordination.activeOperation != null || coordination.inspectionInFlight -> {
                PersistenceTransition(coordination, PersistenceRequestResult.Busy)
            }

            recovery == null -> {
                PersistenceTransition(coordination, PersistenceRequestResult.Stale)
            }

            else -> {
                createRecoveryOperation(coordination, context, recovery, purpose)
            }
        }
    }

    private fun createRecoveryOperation(
        coordination: PersistenceCoordination,
        context: SwitchContext,
        recovery: RuntimeRecoveryState.LegacyCandidate,
        purpose: LegacyImportPurpose,
    ): PersistenceTransition<PersistenceRequestResult> =
        when (val creation = coordination.nextOperationHandle()) {
            is OperationHandleCreation.Created -> {
                val operation =
                    ActivePersistenceOperation.Switch.LegacyImport(
                        identity =
                            LegacyImportOperationIdentity(
                                creation.handle,
                                context.source,
                                LegacyImportSourceIdentity(),
                                LegacyImportSourceOrigin.Recovery(recovery.generation),
                            ),
                        importSource =
                            LegacyImportCandidate(
                                recovery.candidate,
                                LegacySourcePreview(recovery.candidate.source),
                            ),
                        preservation =
                            LegacyImportPreservation(
                                purpose,
                                false,
                                LegacyCopyAttemptOutcome.NotAttempted,
                            ),
                        destinationState =
                            LegacyImportDestination(
                                null,
                                0L,
                                LegacyImportPhase.Required(null),
                            ),
                    )
                PersistenceTransition(
                    creation.next.withActive(operation),
                    PersistenceRequestResult.LegacyConversionRequired(creation.handle),
                )
            }

            OperationHandleCreation.Exhausted -> {
                val exhausted = coordination.exhausted()
                PersistenceTransition(exhausted.next, exhausted.result)
            }
        }

    fun beginCopy(
        coordination: PersistenceCoordination,
        handle: PersistenceOperationHandle,
    ): PersistenceTransition<LegacyCopyStart> {
        val operation = coordination.activeOperation as? ActivePersistenceOperation.Switch.LegacyImport
        val phase = operation?.phase
        return when {
            operation == null || operation.handle != handle -> {
                PersistenceTransition(coordination, LegacyCopyStart.Result(LegacySourceCopyRequestResult.Stale))
            }

            !hasCurrentRecoverySource(coordination, operation) -> {
                PersistenceTransition(
                    coordination.withActive(null),
                    LegacyCopyStart.Result(LegacySourceCopyRequestResult.Stale),
                )
            }

            phase !is LegacyImportPhase.Required -> {
                PersistenceTransition(coordination, LegacyCopyStart.Result(LegacySourceCopyRequestResult.TooLate))
            }

            else -> {
                val next = operation.withPhase(LegacyImportPhase.Copying(phase.preview))
                PersistenceTransition(
                    coordination.withActive(next),
                    LegacyCopyStart.Permit(
                        LegacyCopyPermit(handle, operation.sourceIdentity, operation.candidate.source),
                    ),
                )
            }
        }
    }

    fun completeCopy(
        coordination: PersistenceCoordination,
        permit: LegacyCopyPermit,
        outcome: LegacySourceCopyOutcome,
    ): PersistenceTransition<LegacySourceCopyRequestResult> {
        val operation = coordination.activeOperation as? ActivePersistenceOperation.Switch.LegacyImport
        return when {
            operation == null ||
                operation.handle != permit.handle ||
                operation.sourceIdentity !== permit.sourceIdentity -> {
                PersistenceTransition(coordination, LegacySourceCopyRequestResult.Stale)
            }

            !hasCurrentRecoverySource(coordination, operation) -> {
                PersistenceTransition(coordination.withActive(null), LegacySourceCopyRequestResult.Stale)
            }

            operation.phase is LegacyImportPhase.Cancelling -> {
                cancelledCopy(coordination)
            }

            operation.phase !is LegacyImportPhase.Copying -> {
                PersistenceTransition(coordination, LegacySourceCopyRequestResult.Stale)
            }

            else -> {
                applyCopyOutcome(coordination, operation, outcome)
            }
        }
    }

    private fun applyCopyOutcome(
        coordination: PersistenceCoordination,
        operation: ActivePersistenceOperation.Switch.LegacyImport,
        outcome: LegacySourceCopyOutcome,
    ): PersistenceTransition<LegacySourceCopyRequestResult> {
        val previous = (operation.phase as LegacyImportPhase.Copying).previousPreview
        val required = LegacyImportPhase.Required(previous)
        return when (outcome) {
            LegacySourceCopyOutcome.Copied -> {
                PersistenceTransition(
                    coordination.withActive(
                        operation.withCopyOutcome(true, LegacyCopyAttemptOutcome.Copied, required),
                    ),
                    LegacySourceCopyRequestResult.Copied,
                )
            }

            LegacySourceCopyOutcome.Cancelled -> {
                PersistenceTransition(
                    coordination.withActive(
                        operation.withCopyOutcome(outcome = LegacyCopyAttemptOutcome.Cancelled, phase = required),
                    ),
                    LegacySourceCopyRequestResult.Cancelled,
                )
            }

            is LegacySourceCopyOutcome.Failed -> {
                PersistenceTransition(
                    coordination.withActive(
                        operation.withCopyOutcome(
                            outcome = LegacyCopyAttemptOutcome.Failed(outcome.failure, outcome.cleanup),
                            phase = required,
                        ),
                    ),
                    LegacySourceCopyRequestResult.Failed(outcome.failure, outcome.cleanup),
                )
            }
        }
    }

    fun beginReduction(
        coordination: PersistenceCoordination,
        handle: PersistenceOperationHandle,
        destination: PaletteDefinition,
    ): PersistenceTransition<LegacyReductionStart> {
        val operation = coordination.activeOperation as? ActivePersistenceOperation.Switch.LegacyImport
        return when {
            operation == null || operation.handle != handle -> {
                PersistenceTransition(coordination, LegacyReductionStart.Result(LegacyReductionRequestResult.Stale))
            }

            !hasCurrentRecoverySource(coordination, operation) -> {
                PersistenceTransition(
                    coordination.withActive(null),
                    LegacyReductionStart.Result(LegacyReductionRequestResult.Stale),
                )
            }

            operation.purpose != LegacyImportPurpose.Convert || operation.phase !is LegacyImportPhase.Required -> {
                PersistenceTransition(coordination, LegacyReductionStart.Result(LegacyReductionRequestResult.TooLate))
            }

            operation.destinationEpoch == Long.MAX_VALUE -> {
                PersistenceTransition(
                    coordination.identityExhausted().withActive(null),
                    LegacyReductionStart.Result(LegacyReductionRequestResult.IdentityExhausted),
                )
            }

            else -> {
                val epoch = operation.destinationEpoch + 1L
                val next = operation.withDestination(destination, epoch, LegacyImportPhase.Reducing(destination, epoch))
                val permit =
                    LegacyReductionPermit(
                        LegacyOperationProof(handle, operation.sourceIdentity),
                        operation.candidate,
                        LegacyDestinationBinding(epoch, destination),
                    )
                PersistenceTransition(coordination.withActive(next), LegacyReductionStart.Permit(permit))
            }
        }
    }

    fun completeReduction(
        coordination: PersistenceCoordination,
        permit: LegacyReductionPermit,
        preview: LegacyReductionPreview,
    ): PersistenceTransition<LegacyReductionRequestResult> {
        val operation = coordination.activeOperation as? ActivePersistenceOperation.Switch.LegacyImport
        val phase = operation?.phase as? LegacyImportPhase.Reducing
        return when {
            operation == null ||
                operation.handle != permit.handle ||
                operation.sourceIdentity !== permit.sourceIdentity -> {
                PersistenceTransition(coordination, LegacyReductionRequestResult.Stale)
            }

            !hasCurrentRecoverySource(coordination, operation) -> {
                PersistenceTransition(coordination.withActive(null), LegacyReductionRequestResult.Stale)
            }

            operation.phase is LegacyImportPhase.Cancelling -> {
                val completed = coordination.completed(PersistenceLastOutcome.Cancelled)
                PersistenceTransition(completed.next, LegacyReductionRequestResult.Stale)
            }

            phase == null || phase.epoch != permit.epoch || phase.destination != permit.destination -> {
                PersistenceTransition(coordination, LegacyReductionRequestResult.Stale)
            }

            preview.definition != permit.destination -> {
                PersistenceTransition(coordination, LegacyReductionRequestResult.Stale)
            }

            else -> {
                val handle = LegacyReductionHandle(operation.handle, permit.epoch)
                val bound =
                    BoundLegacyReductionPreview(
                        permit.epoch,
                        permit.destination,
                        preview,
                        LegacyReductionProjection(handle, preview),
                    )
                val next = operation.withPhase(LegacyImportPhase.Required(bound))
                PersistenceTransition(coordination.withActive(next), LegacyReductionRequestResult.Ready(handle))
            }
        }
    }

    fun beginAdoption(
        coordination: PersistenceCoordination,
        handle: LegacyReductionHandle,
        context: SwitchContext,
    ): PersistenceTransition<LegacyAdoptionStart> {
        val operation = coordination.activeOperation as? ActivePersistenceOperation.Switch.LegacyImport
        val bound = (operation?.phase as? LegacyImportPhase.Required)?.preview
        return when {
            operation == null || operation.handle != handle.operation || bound == null ||
                bound.epoch != handle.destinationEpoch -> {
                PersistenceTransition(coordination, LegacyAdoptionStart.Result(PersistenceRequestResult.Stale))
            }

            !hasCurrentRecoverySource(coordination, operation) -> {
                PersistenceTransition(
                    coordination.withActive(null),
                    LegacyAdoptionStart.Result(PersistenceRequestResult.Stale),
                )
            }

            operation.purpose != LegacyImportPurpose.Convert -> {
                PersistenceTransition(coordination, LegacyAdoptionStart.Result(PersistenceRequestResult.TooLate))
            }

            operation.origin is LegacyImportSourceOrigin.Recovery && !operation.originalCopyVerified -> {
                PersistenceTransition(
                    coordination,
                    LegacyAdoptionStart.Result(PersistenceRequestResult.OriginalCopyRequired),
                )
            }

            context.source != operation.consentedSource -> {
                confirmAdoption(coordination, operation, bound, context)
            }

            else -> {
                prepareAdoption(coordination, operation, bound, context)
            }
        }
    }

    private fun confirmAdoption(
        coordination: PersistenceCoordination,
        operation: ActivePersistenceOperation.Switch.LegacyImport,
        bound: BoundLegacyReductionPreview,
        context: SwitchContext,
    ): PersistenceTransition<LegacyAdoptionStart> =
        when (
            val creation =
                coordination.nextConfirmation(
                    operation.handle,
                    ConfirmationPolicy.sourceChangedReason(coordination),
                )
        ) {
            is ConfirmationCreation.Created -> {
                val pending = PendingSwitch.LegacyPrepared(operation, bound)
                val confirming =
                    ActivePersistenceOperation.Switch.Confirming(
                        operation.handle,
                        context.source,
                        pending,
                        creation.request,
                    )
                PersistenceTransition(
                    creation.next.withActive(confirming),
                    LegacyAdoptionStart.Confirmation(creation.request),
                )
            }

            ConfirmationCreation.Exhausted -> {
                val exhausted = coordination.exhausted()
                PersistenceTransition(exhausted.next, LegacyAdoptionStart.Result(exhausted.result))
            }
        }

    fun prepareAdoption(
        coordination: PersistenceCoordination,
        operation: ActivePersistenceOperation.Switch.LegacyImport,
        bound: BoundLegacyReductionPreview,
        context: SwitchContext,
    ): PersistenceTransition<LegacyAdoptionStart> {
        val preparing = operation.withConsentAndPhase(context.source, LegacyImportPhase.Preparing(bound))
        val permit =
            LegacyAdoptionPermit(operation.handle, operation.sourceIdentity, bound.epoch) {
                context.derivedOwners(bound.preview)
            }
        return PersistenceTransition(coordination.withActive(preparing), LegacyAdoptionStart.Permit(permit))
    }

    fun completeAdoptionPreparation(
        coordination: PersistenceCoordination,
        permit: LegacyAdoptionPermit,
        owners: RuntimeOwners,
    ): PersistenceTransition<LegacyPreparationCompletion> {
        val operation = coordination.activeOperation as? ActivePersistenceOperation.Switch.LegacyImport
        val phase = operation?.phase as? LegacyImportPhase.Preparing
        val sameOperation =
            operation != null &&
                operation.handle == permit.handle &&
                operation.sourceIdentity === permit.sourceIdentity
        val samePreview = phase != null && phase.preview.epoch == permit.epoch
        return if (operation?.phase is LegacyImportPhase.Cancelling) {
            val completed = coordination.completed(PersistenceLastOutcome.Cancelled)
            PersistenceTransition(completed.next, LegacyPreparationCompletion.Result(PersistenceRequestResult.Stale))
        } else if (!sameOperation || !samePreview) {
            PersistenceTransition(coordination, LegacyPreparationCompletion.Result(PersistenceRequestResult.Stale))
        } else {
            val recoveryProof =
                when (val origin = operation.origin) {
                    LegacyImportSourceOrigin.UserFile -> {
                        null
                    }

                    is LegacyImportSourceOrigin.Recovery -> {
                        check(operation.originalCopyVerified)
                        VerifiedLegacyRecoveryCommit(origin.generation, operation.candidate.source)
                    }
                }
            val ready =
                ActivePersistenceOperation.Switch.Ready(
                    operation.handle,
                    operation.consentedSource,
                    PreparedSwitch(owners, SwitchKind.LegacyImported, recoveryProof),
                )
            PersistenceTransition(coordination.withActive(ready), LegacyPreparationCompletion.Ready(operation.handle))
        }
    }
}

private fun cancelledCopy(
    coordination: PersistenceCoordination,
): PersistenceTransition<LegacySourceCopyRequestResult> {
    val completed = coordination.completed(PersistenceLastOutcome.Cancelled)
    return PersistenceTransition(completed.next, LegacySourceCopyRequestResult.Cancelled)
}

private fun hasCurrentRecoverySource(
    coordination: PersistenceCoordination,
    operation: ActivePersistenceOperation.Switch.LegacyImport,
): Boolean {
    val origin = operation.origin
    val recovery = coordination.recoveryState as? RuntimeRecoveryState.LegacyCandidate
    return if (origin is LegacyImportSourceOrigin.Recovery) {
        recovery != null &&
            recovery.generation == origin.generation &&
            recovery.candidate.source === operation.candidate.source
    } else {
        true
    }
}
