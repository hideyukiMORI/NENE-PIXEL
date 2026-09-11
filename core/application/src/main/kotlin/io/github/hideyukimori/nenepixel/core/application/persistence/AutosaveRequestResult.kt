package io.github.hideyukimori.nenepixel.core.application.persistence

public sealed interface AutosaveRequestResult {
    public data class Published internal constructor(
        public val generation: RecoveryGeneration,
    ) : AutosaveRequestResult

    public data object NoCapture : AutosaveRequestResult

    public data object Deferred : AutosaveRequestResult

    public data object OfferPending : AutosaveRequestResult

    public data object Unavailable : AutosaveRequestResult

    public data object Stale : AutosaveRequestResult

    public data object GenerationExhausted : AutosaveRequestResult

    public data object IdentityExhausted : AutosaveRequestResult

    public data class Failed internal constructor(
        public val failure: RecoveryRetirementFailure,
        public val rollback: RecoveryRollbackOutcome,
    ) : AutosaveRequestResult

    public data class Uncertain internal constructor(
        public val failure: RecoveryRetirementFailure,
        public val rollback: RecoveryRollbackOutcome,
    ) : AutosaveRequestResult
}
