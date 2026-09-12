package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
        Button(
            colors = editorButtonColors(),
            enabled = idle && operation.recoveryStatus !is RecoveryStatus.Initializing,
            onClick = {
                callbacks.onSaveAs()
                submitted()
            },
        ) {
            Text("Save As")
        }
        Button(colors = editorButtonColors(), enabled = switchAvailable, onClick = {
            callbacks.onLoad()
            submitted()
        }) {
            Text("Load")
        }
        NewDocumentControls(
            canvasSize = canvasSize,
            callbacks = callbacks,
            enabled = switchAvailable,
            submitted = submitted,
        )
        Button(colors = editorButtonColors(), enabled = idle, onClick = {
            callbacks.onExportPng()
            submitted()
        }) {
            Text("Export PNG")
        }
        operation.phase.cancellableOperation()?.let { handle ->
            Button(colors = editorButtonColors(), onClick = { callbacks.onCancel(handle) }) {
                Text("Cancel operation")
            }
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
        onDismissRequest = { callbacks.onCancel(request.operation) },
        title = { Text("Discard current work?") },
        text = { Text(request.reason.confirmationMessage()) },
        confirmButton = {
            Button(colors = editorButtonColors(), onClick = { callbacks.onConfirm(request) }) {
                Text("Discard and continue")
            }
        },
        dismissButton = {
            TextButton(onClick = { callbacks.onCancel(request.operation) }) {
                Text("Keep current work")
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

internal fun PersistenceOperationProjection.statusText(autosave: AutosaveProjection): String =
    when (phase) {
        PersistenceOperationPhase.Initializing -> "Checking recovery data"
        PersistenceOperationPhase.Idle -> idleStatusText(autosave)
        is PersistenceOperationPhase.Exporting -> "Exporting PNG"
        is PersistenceOperationPhase.Saving -> "Saving project"
        is PersistenceOperationPhase.Loading -> "Loading project"
        is PersistenceOperationPhase.NeedsConfirmation -> "Waiting for confirmation"
        is PersistenceOperationPhase.Switching -> "Switching document"
        is PersistenceOperationPhase.Cancelling -> "Cancelling operation"
        is PersistenceOperationPhase.Discarding -> "Discarding recovery data"
    }

private fun PersistenceOperationProjection.idleStatusText(autosave: AutosaveProjection): String =
    when {
        (lastOutcome as? PersistenceLastOutcome.Failed)?.failure is PersistenceFailure.PngExport -> "PNG export failed"
        recoveryStatus is RecoveryStatus.Unknown -> "Recovery data is unavailable"
        recoveryStatus is RecoveryStatus.UnadoptedCandidate -> "Recovery data will be preserved"
        autosave.lastOutcome is AutosaveLastOutcome.Failed -> "Autosave failed"
        autosave.lastOutcome is AutosaveLastOutcome.Uncertain -> "Autosave result is uncertain"
        lastOutcome is PersistenceLastOutcome.PngExported -> "PNG exported"
        lastOutcome is PersistenceLastOutcome.Saved -> "Project saved"
        lastOutcome is PersistenceLastOutcome.Loaded -> "Project loaded"
        lastOutcome is PersistenceLastOutcome.NewDocumentCreated -> "New document created"
        lastOutcome is PersistenceLastOutcome.Cancelled -> "Operation cancelled"
        lastOutcome is PersistenceLastOutcome.Failed -> "Project operation failed"
        lastOutcome is PersistenceLastOutcome.None -> "Project storage ready"
        else -> "Project storage ready"
    }

private fun PersistenceConfirmationReason.confirmationMessage(): String =
    when (this) {
        PersistenceConfirmationReason.DISCARD_CURRENT_CHANGES -> {
            "Discard unsaved document changes?"
        }

        PersistenceConfirmationReason.DISCARD_RECOVERY_CANDIDATE -> {
            "Discard the preserved recovery data?"
        }

        PersistenceConfirmationReason.DISCARD_CURRENT_CHANGES_AND_RECOVERY_CANDIDATE -> {
            "Discard unsaved document changes and the preserved recovery data?"
        }

        PersistenceConfirmationReason.SOURCE_CHANGED -> {
            "The current document changed. Discard it and continue?"
        }

        PersistenceConfirmationReason.SOURCE_CHANGED_AND_RECOVERY_CANDIDATE -> {
            "The current document changed. Discard it and the preserved recovery data?"
        }
    }

private val PERSISTENCE_SPACING = 8.dp
