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
    /** Picks the reference image shown under the drawing (ADR 0032). */
    internal val pickReferenceImage: () -> Unit = files.exchange.pickReferenceImage

    /** Picks a PNG to import; a picked PNG waits in the workspace for the user's choice (ADR 0033). */
    internal val importPng: () -> Unit = files.exchange.importPng

    internal fun onExportPng() {
        files.exchange.exportPng()
    }

    internal fun onExportPaletteJson() {
        files.exchange.exportPaletteJson()
    }

    internal fun onImportPaletteJson() {
        files.exchange.importPaletteJson()
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
