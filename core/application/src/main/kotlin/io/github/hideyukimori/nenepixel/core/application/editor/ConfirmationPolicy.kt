package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceConfirmationReason

internal sealed interface ConfirmationNeed {
    data object NotRequired : ConfirmationNeed

    data class Required(
        val reason: PersistenceConfirmationReason,
    ) : ConfirmationNeed
}

internal object ConfirmationPolicy {
    fun initialNeed(
        coordination: PersistenceCoordination,
        dirty: Boolean,
    ): ConfirmationNeed = need(dirty, coordination.recoveryState is RuntimeRecoveryState.Candidate)

    fun sourceChangedReason(coordination: PersistenceCoordination): PersistenceConfirmationReason =
        if (coordination.recoveryState is RuntimeRecoveryState.Candidate) {
            PersistenceConfirmationReason.SOURCE_CHANGED_AND_RECOVERY_CANDIDATE
        } else {
            PersistenceConfirmationReason.SOURCE_CHANGED
        }

    fun refreshedReason(
        coordination: PersistenceCoordination,
        pending: PendingSwitch,
        dirty: Boolean,
    ): PersistenceConfirmationReason =
        when (pending) {
            is PendingSwitch.Recover -> PersistenceConfirmationReason.DISCARD_CURRENT_CHANGES
            is PendingSwitch.Prepared -> sourceChangedReason(coordination)
            else -> currentReason(coordination, dirty)
        }

    private fun currentReason(
        coordination: PersistenceCoordination,
        dirty: Boolean,
    ): PersistenceConfirmationReason =
        when (val current = initialNeed(coordination, dirty)) {
            is ConfirmationNeed.Required -> current.reason
            ConfirmationNeed.NotRequired -> PersistenceConfirmationReason.SOURCE_CHANGED
        }

    private fun need(
        dirty: Boolean,
        candidate: Boolean,
    ): ConfirmationNeed =
        when {
            dirty && candidate -> {
                ConfirmationNeed.Required(
                    PersistenceConfirmationReason.DISCARD_CURRENT_CHANGES_AND_RECOVERY_CANDIDATE,
                )
            }

            dirty -> {
                ConfirmationNeed.Required(PersistenceConfirmationReason.DISCARD_CURRENT_CHANGES)
            }

            candidate -> {
                ConfirmationNeed.Required(PersistenceConfirmationReason.DISCARD_RECOVERY_CANDIDATE)
            }

            else -> {
                ConfirmationNeed.NotRequired
            }
        }
}
