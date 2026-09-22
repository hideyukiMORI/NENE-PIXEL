package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRequest
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceConfirmationRequest
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle

public class EditorPersistenceCallbacks private constructor(
    private val files: ProjectFileCallbacks,
    private val decisions: PersistenceDecisionCallbacks,
    internal val conversion: LegacyConversionCallbacks,
    internal val presets: LegacyPalettePresets,
) {
    internal fun onExportPng() {
        files.exportPng()
    }

    internal fun onSaveAs() {
        files.saveAs()
    }

    internal fun onLoad() {
        files.load()
    }

    internal fun onCreateNewDocument(
        rawWidth: String,
        rawHeight: String,
    ): NewDocumentSubmission =
        when (val request = NewDocumentRequest.create(rawWidth, rawHeight)) {
            is NewDocumentRequestResult.Created -> {
                files.createNewDocument(request)
                NewDocumentSubmission.Submitted
            }

            is NewDocumentRequestResult.Rejected -> {
                NewDocumentSubmission.Rejected(request.rejection)
            }
        }

    internal fun onConfirm(request: PersistenceConfirmationRequest) {
        decisions.confirm(request)
    }

    internal fun onCancel(operation: PersistenceOperationHandle) {
        decisions.cancel(operation)
    }

    internal fun onAcceptRecovery() {
        decisions.acceptRecovery()
    }

    internal fun onDeclineRecovery() {
        decisions.declineRecovery()
    }

    public companion object {
        public fun create(
            files: ProjectFileCallbacks,
            decisions: PersistenceDecisionCallbacks,
            conversion: LegacyConversionCallbacks,
            presets: LegacyPalettePresets,
        ): EditorPersistenceCallbacks = EditorPersistenceCallbacks(files, decisions, conversion, presets)
    }
}
