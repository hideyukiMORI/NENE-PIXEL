package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceConfirmationReason
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceConfirmationRequest
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryStatus
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.presentation.compose.R

@Composable
internal fun PersistenceControls(
    operation: PersistenceOperationProjection,
    canvasSize: CanvasSize,
    callbacks: EditorPersistenceCallbacks,
    submitted: () -> Unit,
) {
    val idle = operation.phase is PersistenceOperationPhase.Idle
    val switchAvailable = idle && operation.recoveryStatus.isAvailableForDocumentSwitch()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(PERSISTENCE_SPACING)) {
        EditorActionButton(R.string.save_as, idle && operation.recoveryStatus !is RecoveryStatus.Initializing) {
            callbacks.onSaveAs()
            submitted()
        }
        EditorActionButton(R.string.load, switchAvailable) {
            callbacks.onLoad()
            submitted()
        }
        NewDocumentControls(canvasSize, callbacks, switchAvailable, submitted)
        EditorActionButton(R.string.export_png, idle) {
            callbacks.onExportPng()
            submitted()
        }
        operation.phase.cancellableOperation()?.let { handle ->
            EditorActionButton(R.string.cancel_operation) { callbacks.onCancel(handle) }
        }
    }
}

@Composable
internal fun PersistenceConfirmation(
    operation: PersistenceOperationProjection,
    callbacks: EditorPersistenceCallbacks,
) {
    (operation.phase as? PersistenceOperationPhase.NeedsConfirmation)?.let { phase ->
        DiscardConfirmationDialog(phase.request, callbacks)
    }
}

@Composable
private fun DiscardConfirmationDialog(
    request: PersistenceConfirmationRequest,
    callbacks: EditorPersistenceCallbacks,
) {
    AlertDialog(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        onDismissRequest = { callbacks.onCancel(request.operation) },
        title = { Text(stringResource(R.string.discard_current_title)) },
        text = { Text(stringResource(request.reason.confirmationResource())) },
        confirmButton = {
            Button(
                colors = editorButtonColors(),
                modifier =
                    Modifier.editorDescription(
                        R.string.discard_continue,
                    ),
                onClick = {
                    callbacks.onConfirm(request)
                },
            ) {
                Text(stringResource(R.string.discard_continue))
            }
        },
        dismissButton = {
            TextButton(modifier = Modifier.editorDescription(R.string.keep_current), onClick = {
                callbacks.onCancel(request.operation)
            }) {
                Text(stringResource(R.string.keep_current))
            }
        },
    )
}

private fun PersistenceOperationPhase.cancellableOperation(): PersistenceOperationHandle? =
    when (this) {
        is PersistenceOperationPhase.Exporting -> operation

        is PersistenceOperationPhase.Saving -> operation

        is PersistenceOperationPhase.Loading -> operation

        is PersistenceOperationPhase.Discarding -> operation

        PersistenceOperationPhase.Idle,
        PersistenceOperationPhase.Initializing,
        is PersistenceOperationPhase.NeedsConfirmation,
        is PersistenceOperationPhase.Switching,
        is PersistenceOperationPhase.Cancelling,
        -> null
    }

internal fun RecoveryStatus.isAvailableForDocumentSwitch(): Boolean =
    this is RecoveryStatus.Clear || this is RecoveryStatus.UnadoptedCandidate

internal fun PersistenceOperationProjection.statusResource(autosave: AutosaveProjection): Int =
    when (phase) {
        PersistenceOperationPhase.Initializing -> R.string.checking_recovery
        PersistenceOperationPhase.Idle -> idleStatusResource(autosave)
        is PersistenceOperationPhase.Exporting -> R.string.exporting_png
        is PersistenceOperationPhase.Saving -> R.string.saving_project
        is PersistenceOperationPhase.Loading -> R.string.loading_project
        is PersistenceOperationPhase.NeedsConfirmation -> R.string.waiting_confirmation
        is PersistenceOperationPhase.Switching -> R.string.switching_document
        is PersistenceOperationPhase.Cancelling -> R.string.cancelling_operation
        is PersistenceOperationPhase.Discarding -> R.string.discarding_recovery
    }

private fun PersistenceOperationProjection.idleStatusResource(autosave: AutosaveProjection): Int =
    when {
        (lastOutcome as? PersistenceLastOutcome.Failed)?.failure is PersistenceFailure.PngExport -> {
            R.string.png_export_failed
        }

        recoveryStatus is RecoveryStatus.Unknown -> {
            R.string.recovery_unavailable
        }

        recoveryStatus is RecoveryStatus.UnadoptedCandidate -> {
            R.string.recovery_preserved
        }

        autosave.lastOutcome is AutosaveLastOutcome.Failed -> {
            R.string.autosave_failed
        }

        autosave.lastOutcome is AutosaveLastOutcome.Uncertain -> {
            R.string.autosave_uncertain
        }

        lastOutcome is PersistenceLastOutcome.PngExported -> {
            R.string.png_exported
        }

        lastOutcome is PersistenceLastOutcome.Saved -> {
            R.string.project_saved
        }

        lastOutcome is PersistenceLastOutcome.Loaded -> {
            R.string.project_loaded
        }

        lastOutcome is PersistenceLastOutcome.NewDocumentCreated -> {
            R.string.new_document_created
        }

        lastOutcome is PersistenceLastOutcome.Cancelled -> {
            R.string.operation_cancelled
        }

        lastOutcome is PersistenceLastOutcome.Failed -> {
            R.string.operation_failed
        }

        else -> {
            R.string.storage_ready
        }
    }

private fun PersistenceConfirmationReason.confirmationResource(): Int =
    when (this) {
        PersistenceConfirmationReason.DISCARD_CURRENT_CHANGES -> {
            R.string.confirm_current
        }

        PersistenceConfirmationReason.DISCARD_RECOVERY_CANDIDATE -> {
            R.string.confirm_recovery
        }

        PersistenceConfirmationReason.DISCARD_CURRENT_CHANGES_AND_RECOVERY_CANDIDATE -> {
            R.string.confirm_current_recovery
        }

        PersistenceConfirmationReason.SOURCE_CHANGED -> {
            R.string.confirm_changed
        }

        PersistenceConfirmationReason.SOURCE_CHANGED_AND_RECOVERY_CANDIDATE -> {
            R.string.confirm_changed_recovery
        }
    }

private val PERSISTENCE_SPACING = 8.dp
