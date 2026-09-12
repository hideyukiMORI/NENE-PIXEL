package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRequest
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceConfirmationRequest
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle

public class EditorPersistenceCallbacks private constructor(
    private val exportPng: () -> Unit,
    private val saveAs: () -> Unit,
    private val load: () -> Unit,
    private val createNewDocument: (NewDocumentRequestResult) -> Unit,
    private val confirm: (PersistenceConfirmationRequest) -> Unit,
    private val cancel: (PersistenceOperationHandle) -> Unit,
    private val acceptRecovery: () -> Unit,
    private val declineRecovery: () -> Unit,
) {
    internal fun onExportPng() {
        exportPng()
    }

    internal fun onSaveAs() {
        saveAs()
    }

    internal fun onLoad() {
        load()
    }

    internal fun onCreateNewDocument(
        rawWidth: String,
        rawHeight: String,
    ): NewDocumentSubmission =
        when (val request = NewDocumentRequest.create(rawWidth, rawHeight)) {
            is NewDocumentRequestResult.Created -> {
                createNewDocument(request)
                NewDocumentSubmission.Submitted
            }

            is NewDocumentRequestResult.Rejected -> {
                NewDocumentSubmission.Rejected(request.rejection)
            }
        }

    internal fun onConfirm(request: PersistenceConfirmationRequest) {
        confirm(request)
    }

    internal fun onCancel(operation: PersistenceOperationHandle) {
        cancel(operation)
    }

    internal fun onAcceptRecovery() {
        acceptRecovery()
    }

    internal fun onDeclineRecovery() {
        declineRecovery()
    }

    public companion object {
        public fun create(
            exportPng: () -> Unit,
            saveAs: () -> Unit,
            load: () -> Unit,
            createNewDocument: (NewDocumentRequestResult) -> Unit,
            confirm: (PersistenceConfirmationRequest) -> Unit,
            cancel: (PersistenceOperationHandle) -> Unit,
            acceptRecovery: () -> Unit,
            declineRecovery: () -> Unit,
        ): EditorPersistenceCallbacks =
            EditorPersistenceCallbacks(
                exportPng,
                saveAs,
                load,
                createNewDocument,
                confirm,
                cancel,
                acceptRecovery,
                declineRecovery,
            )
    }
}
