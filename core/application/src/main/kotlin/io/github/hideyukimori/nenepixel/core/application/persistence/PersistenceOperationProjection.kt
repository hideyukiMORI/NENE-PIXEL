package io.github.hideyukimori.nenepixel.core.application.persistence

public data class PersistenceOperationProjection internal constructor(
    public val phase: PersistenceOperationPhase,
    public val lastOutcome: PersistenceLastOutcome,
    public val recoveryStatus: RecoveryStatus,
)

public sealed interface PersistenceOperationPhase {
    public data object Initializing : PersistenceOperationPhase

    public data object Idle : PersistenceOperationPhase

    public data class Saving internal constructor(
        public val operation: PersistenceOperationHandle,
    ) : PersistenceOperationPhase

    public data class Loading internal constructor(
        public val operation: PersistenceOperationHandle,
    ) : PersistenceOperationPhase

    public data class NeedsConfirmation internal constructor(
        public val request: PersistenceConfirmationRequest,
    ) : PersistenceOperationPhase

    public data class Switching internal constructor(
        public val operation: PersistenceOperationHandle,
    ) : PersistenceOperationPhase

    public data class Cancelling internal constructor(
        public val operation: PersistenceOperationHandle,
    ) : PersistenceOperationPhase

    public data class Discarding internal constructor(
        public val operation: PersistenceOperationHandle,
    ) : PersistenceOperationPhase
}

public class PersistenceOperationHandle internal constructor(
    internal val value: Long,
) {
    public override fun toString(): String = "PersistenceOperationHandle"
}

public class PersistenceConfirmationRequest internal constructor(
    public val operation: PersistenceOperationHandle,
    public val reason: PersistenceConfirmationReason,
    internal val confirmationId: Long,
) {
    public override fun equals(other: Any?): Boolean =
        other is PersistenceConfirmationRequest &&
            operation == other.operation &&
            confirmationId == other.confirmationId

    public override fun hashCode(): Int = 31 * operation.hashCode() + confirmationId.hashCode()

    public override fun toString(): String = "PersistenceConfirmationRequest(reason=$reason)"
}

public enum class PersistenceConfirmationReason {
    DISCARD_CURRENT_CHANGES,
    DISCARD_RECOVERY_CANDIDATE,
    DISCARD_CURRENT_CHANGES_AND_RECOVERY_CANDIDATE,
    SOURCE_CHANGED,
    SOURCE_CHANGED_AND_RECOVERY_CANDIDATE,
}

public sealed interface PersistenceLastOutcome {
    public data object None : PersistenceLastOutcome

    public data class Saved internal constructor(
        public val recoveryCleanup: RecoveryCleanupOutcome,
    ) : PersistenceLastOutcome

    public data object Loaded : PersistenceLastOutcome

    public data object NewDocumentCreated : PersistenceLastOutcome

    public data object Recovered : PersistenceLastOutcome

    public data object RecoveryDeclined : PersistenceLastOutcome

    public data object Cancelled : PersistenceLastOutcome

    public data class Failed internal constructor(
        public val failure: PersistenceFailure,
    ) : PersistenceLastOutcome
}

public sealed interface RecoveryCleanupOutcome {
    public data class Retired internal constructor(
        public val generation: RecoveryGeneration,
    ) : RecoveryCleanupOutcome

    public data object PreservedUnadoptedCandidate : RecoveryCleanupOutcome

    public data object RecoveryUnavailable : RecoveryCleanupOutcome

    public data object Stale : RecoveryCleanupOutcome

    public data object GenerationExhausted : RecoveryCleanupOutcome

    public data class Failed internal constructor(
        public val failure: RecoveryRetirementFailure,
        public val rollback: RecoveryRollbackOutcome,
    ) : RecoveryCleanupOutcome

    public data class Uncertain internal constructor(
        public val failure: RecoveryRetirementFailure,
        public val rollback: RecoveryRollbackOutcome,
    ) : RecoveryCleanupOutcome
}

public sealed interface RecoveryStatus {
    public data object Initializing : RecoveryStatus

    public data object Clear : RecoveryStatus

    public data object UnadoptedCandidate : RecoveryStatus

    public data class Unknown internal constructor(
        public val reason: RecoveryUnavailableReason,
    ) : RecoveryStatus
}

public sealed interface RecoveryUnavailableReason {
    public data class Inspection internal constructor(
        public val failure: RecoveryInspectionFailure,
    ) : RecoveryUnavailableReason

    public data object Stale : RecoveryUnavailableReason

    public data class RetirementUncertain internal constructor(
        public val failure: RecoveryRetirementFailure,
        public val rollback: RecoveryRollbackOutcome,
    ) : RecoveryUnavailableReason
}

public sealed interface PersistenceFailure {
    public data object IdentityExhausted : PersistenceFailure

    public data class Storage internal constructor(
        public val failure: ProjectStorageFailure,
        public val cleanup: PartialOutputCleanup,
    ) : PersistenceFailure

    public data class RecoveryInspection internal constructor(
        public val failure: RecoveryInspectionFailure,
    ) : PersistenceFailure

    public data object RecoveryLineageChanged : PersistenceFailure

    public data object RecoveryGenerationExhausted : PersistenceFailure

    public data class RecoveryRetirementFailed internal constructor(
        public val failure: RecoveryRetirementFailure,
        public val rollback: RecoveryRollbackOutcome,
    ) : PersistenceFailure

    public data class RecoveryRetirementUncertain internal constructor(
        public val failure: RecoveryRetirementFailure,
        public val rollback: RecoveryRollbackOutcome,
    ) : PersistenceFailure
}
