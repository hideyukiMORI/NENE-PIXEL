package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPosition
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceConfirmationRequest
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

internal sealed interface ActivePersistenceOperation {
    val handle: PersistenceOperationHandle

    data class Save(
        override val handle: PersistenceOperationHandle,
        val capture: SaveCapture,
        val phase: SavePhase,
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
            val candidate: RuntimeOwners,
            val kind: SwitchKind,
        ) : Switch

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

internal enum class SavePhase { Transport, Cleanup, Cancelling }

internal data class SaveCapture(
    val document: DocumentState,
    val historyPosition: HistoryPosition,
    val runtimeGeneration: Long,
    val recovery: SaveRecoveryCapture,
)

internal enum class SwitchKind { Loaded, NewDocument }

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
    ) : PendingSwitch
}

internal fun ActivePersistenceOperation?.isSwitching(): Boolean = this is ActivePersistenceOperation.Switch.Switching

internal fun ActivePersistenceOperation.isCancelling(): Boolean =
    (this is ActivePersistenceOperation.Save && phase == SavePhase.Cancelling) ||
        this is ActivePersistenceOperation.Switch.Cancelling

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
        -> PersistenceOperationPhase.Loading(handle)

        is ActivePersistenceOperation.Switch.Confirming -> PersistenceOperationPhase.NeedsConfirmation(request)

        is ActivePersistenceOperation.Switch.Switching -> PersistenceOperationPhase.Switching(handle)

        is ActivePersistenceOperation.Switch.Cancelling -> PersistenceOperationPhase.Cancelling(handle)
    }

internal fun SwitchIntent.toPendingSwitch(): PendingSwitch =
    when (this) {
        SwitchIntent.Load -> PendingSwitch.Load
        is SwitchIntent.New -> PendingSwitch.New(request)
    }
