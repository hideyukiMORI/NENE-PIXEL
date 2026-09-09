package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRejection

public sealed interface PersistenceRequestResult {
    public data class Completed internal constructor(
        public val outcome: PersistenceLastOutcome,
    ) : PersistenceRequestResult

    public data class AwaitingConfirmation internal constructor(
        public val request: PersistenceConfirmationRequest,
    ) : PersistenceRequestResult

    public data object Busy : PersistenceRequestResult

    public data object Stale : PersistenceRequestResult

    public data object TooLate : PersistenceRequestResult

    public data class Rejected internal constructor(
        public val rejection: NewDocumentRejection,
    ) : PersistenceRequestResult

    public data object RecoveryUnavailable : PersistenceRequestResult
}

public sealed interface PersistenceCancellationResult {
    public data object CancellationStarted : PersistenceCancellationResult

    public data object Cancelled : PersistenceCancellationResult

    public data object Idle : PersistenceCancellationResult

    public data object Stale : PersistenceCancellationResult

    public data object TooLate : PersistenceCancellationResult
}

public sealed interface RecoveryInitializationResult {
    public data object Ready : RecoveryInitializationResult

    public data object AlreadyReady : RecoveryInitializationResult

    public data object Busy : RecoveryInitializationResult

    public data class Failed internal constructor(
        public val failure: RecoveryInspectionFailure,
    ) : RecoveryInitializationResult
}
