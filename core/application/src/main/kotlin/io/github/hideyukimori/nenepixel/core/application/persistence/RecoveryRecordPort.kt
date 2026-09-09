package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

public interface RecoveryRecordPort {
    public suspend fun inspect(): RecoveryInspection

    public suspend fun retire(expected: ExpectedRecoveryLineage): RecoveryRetirementOutcome
}

public class RecoveryGeneration private constructor(
    public val value: Long,
) {
    public override fun equals(other: Any?): Boolean = other is RecoveryGeneration && value == other.value

    public override fun hashCode(): Int = value.hashCode()

    public override fun toString(): String = "RecoveryGeneration(value=$value)"

    public companion object {
        public fun create(value: Long): RecoveryGenerationResult =
            if (value > 0L) {
                RecoveryGenerationResult.Created(RecoveryGeneration(value))
            } else {
                RecoveryGenerationResult.Rejected
            }
    }
}

public sealed interface RecoveryGenerationResult {
    public data class Created internal constructor(
        public val generation: RecoveryGeneration,
    ) : RecoveryGenerationResult

    public data object Rejected : RecoveryGenerationResult
}

public sealed interface ExpectedRecoveryLineage {
    public data object Missing : ExpectedRecoveryLineage

    public data class Present(
        public val generation: RecoveryGeneration,
    ) : ExpectedRecoveryLineage
}

public sealed interface RecoveryInspection {
    public data object Missing : RecoveryInspection

    public data class Retired(
        public val generation: RecoveryGeneration,
    ) : RecoveryInspection

    public data class Candidate(
        public val generation: RecoveryGeneration,
        public val document: DocumentState,
    ) : RecoveryInspection

    public data class Failed(
        public val failure: RecoveryInspectionFailure,
    ) : RecoveryInspection
}

public enum class RecoveryInspectionFailure {
    RESOURCE_LIMIT_EXCEEDED,
    UNSUPPORTED_VERSION,
    CORRUPT,
    READ_FAILED,
    CLOSE_FAILED,
}

public sealed interface RecoveryRetirementOutcome {
    public data class Retired(
        public val generation: RecoveryGeneration,
    ) : RecoveryRetirementOutcome

    public data object Stale : RecoveryRetirementOutcome

    public data object GenerationExhausted : RecoveryRetirementOutcome

    public data class Failed(
        public val failure: RecoveryRetirementFailure,
        public val rollback: RecoveryRollbackOutcome,
    ) : RecoveryRetirementOutcome

    public data class Uncertain(
        public val failure: RecoveryRetirementFailure,
        public val rollback: RecoveryRollbackOutcome,
    ) : RecoveryRetirementOutcome
}

public enum class RecoveryRetirementFailure {
    CURRENT_RECORD_INSPECTION,
    RETIRED_ENCODING,
    START_WRITE,
    WRITE,
    SYNC,
    FINISH,
    READ_BACK,
    READ_BACK_MISMATCH,
}

public enum class RecoveryRollbackOutcome {
    NOT_NEEDED,
    COMPLETED,
    FAILED,
}
