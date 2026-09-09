package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryStatus
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryUnavailableReason
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

internal sealed interface RuntimeRecoveryState {
    data object Initializing : RuntimeRecoveryState

    data class Clear(
        val expected: ExpectedRecoveryLineage,
    ) : RuntimeRecoveryState

    data class Candidate(
        val generation: RecoveryGeneration,
        val document: DocumentState,
    ) : RuntimeRecoveryState

    data class Unknown(
        val reason: RecoveryUnavailableReason,
    ) : RuntimeRecoveryState
}

internal sealed interface SaveRecoveryCapture {
    data class Clear(
        val expected: ExpectedRecoveryLineage,
    ) : SaveRecoveryCapture

    data object UnadoptedCandidate : SaveRecoveryCapture

    data object Unavailable : SaveRecoveryCapture
}

internal sealed interface ExpectedLineageResult {
    data class Available(
        val expected: ExpectedRecoveryLineage,
    ) : ExpectedLineageResult

    data object Unavailable : ExpectedLineageResult
}

internal fun RuntimeRecoveryState.toProjection(): RecoveryStatus =
    when (this) {
        RuntimeRecoveryState.Initializing -> RecoveryStatus.Initializing
        is RuntimeRecoveryState.Clear -> RecoveryStatus.Clear
        is RuntimeRecoveryState.Candidate -> RecoveryStatus.UnadoptedCandidate
        is RuntimeRecoveryState.Unknown -> RecoveryStatus.Unknown(reason)
    }

internal fun RuntimeRecoveryState.toSaveCapture(): SaveRecoveryCapture =
    when (this) {
        is RuntimeRecoveryState.Clear -> SaveRecoveryCapture.Clear(expected)
        is RuntimeRecoveryState.Candidate -> SaveRecoveryCapture.UnadoptedCandidate
        is RuntimeRecoveryState.Unknown -> SaveRecoveryCapture.Unavailable
        RuntimeRecoveryState.Initializing -> SaveRecoveryCapture.Unavailable
    }

internal fun RuntimeRecoveryState.expectedLineage(): ExpectedLineageResult =
    when (this) {
        is RuntimeRecoveryState.Clear -> {
            ExpectedLineageResult.Available(expected)
        }

        is RuntimeRecoveryState.Candidate -> {
            ExpectedLineageResult.Available(
                ExpectedRecoveryLineage.Present(generation),
            )
        }

        RuntimeRecoveryState.Initializing, is RuntimeRecoveryState.Unknown -> {
            ExpectedLineageResult.Unavailable
        }
    }

internal fun RuntimeRecoveryState.isReady(): Boolean =
    this is RuntimeRecoveryState.Clear || this is RuntimeRecoveryState.Candidate

internal fun RuntimeRecoveryState.blocksSwitch(): Boolean =
    this is RuntimeRecoveryState.Initializing || this is RuntimeRecoveryState.Unknown
