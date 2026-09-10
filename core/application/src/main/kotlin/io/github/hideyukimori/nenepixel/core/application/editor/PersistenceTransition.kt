package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult

internal data class PersistenceTransition<out R>(
    val next: PersistenceCoordination,
    val result: R,
    val effect: RuntimeOwnerEffect = RuntimeOwnerEffect.None,
)

internal sealed interface RuntimeOwnerEffect {
    data object None : RuntimeOwnerEffect

    data object CancelPreview : RuntimeOwnerEffect

    data class InstallCleanCheckpoint(
        val checkpoint: DocumentCleanCheckpoint,
    ) : RuntimeOwnerEffect

    data class ReplaceOwners(
        val owners: RuntimeOwners,
    ) : RuntimeOwnerEffect
}

internal fun PersistenceCoordination.completed(
    outcome: PersistenceLastOutcome,
): PersistenceTransition<PersistenceRequestResult.Completed> =
    PersistenceTransition(finished(outcome), PersistenceRequestResult.Completed(outcome))

internal fun PersistenceCoordination.cancelled(): PersistenceTransition<PersistenceRequestResult.Completed> =
    completed(PersistenceLastOutcome.Cancelled)

internal fun PersistenceCoordination.exhausted(): PersistenceTransition<PersistenceRequestResult.Completed> =
    completed(PersistenceLastOutcome.Failed(PersistenceFailure.IdentityExhausted))
