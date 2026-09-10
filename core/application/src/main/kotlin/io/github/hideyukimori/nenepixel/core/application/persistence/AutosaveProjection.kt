package io.github.hideyukimori.nenepixel.core.application.persistence

public data class AutosaveProjection internal constructor(
    public val pendingRevision: Long?,
    public val publishedRevision: Long?,
    public val publishing: Boolean,
    public val lastOutcome: AutosaveLastOutcome,
)

public sealed interface AutosaveLastOutcome {
    public data object None : AutosaveLastOutcome

    public data class Published internal constructor(
        public val generation: RecoveryGeneration,
    ) : AutosaveLastOutcome

    public data object Deferred : AutosaveLastOutcome

    public data object OfferPending : AutosaveLastOutcome

    public data object Stale : AutosaveLastOutcome

    public data object GenerationExhausted : AutosaveLastOutcome

    public data class Failed internal constructor(
        public val failure: RecoveryRetirementFailure,
        public val rollback: RecoveryRollbackOutcome,
    ) : AutosaveLastOutcome

    public data class Uncertain internal constructor(
        public val failure: RecoveryRetirementFailure,
        public val rollback: RecoveryRollbackOutcome,
    ) : AutosaveLastOutcome
}
