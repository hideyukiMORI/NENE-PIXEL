package io.github.hideyukimori.nenepixel

import android.app.Application
import android.util.AtomicFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.hideyukimori.nenepixel.adapters.persistence.AndroidPngExportAdapter
import io.github.hideyukimori.nenepixel.adapters.persistence.AndroidProjectStorageAdapter
import io.github.hideyukimori.nenepixel.adapters.persistence.AndroidRecoveryRecordAdapter
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.EditorPersistenceWorkflow
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceCancellationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorController
import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorPersistenceCallbacks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

internal class EditorRuntimeViewModel private constructor(
    val runtime: EditorRuntime,
    val controller: EditorController,
    val pickerBroker: ProjectPickerBroker,
    private val persistence: EditorPersistenceWorkflow,
) : ViewModel() {
    private val operationWorker = EditorOperationWorker(viewModelScope)

    private val autosave = AutosaveScheduler(persistence)

    val persistenceOperations: StateFlow<PersistenceOperationProjection> = persistence.operation
    val autosaveStates: StateFlow<AutosaveProjection> = persistence.autosave
    val persistenceCallbacks: EditorPersistenceCallbacks =
        EditorPersistenceCallbacks.create(
            saveAs = { launchOperation(persistence::saveAs) },
            exportPng = { launchOperation(persistence::exportPng) },
            load = { launchOperation(persistence::load) },
            createNewDocument = { request ->
                launchOperation { persistence.createNewDocument(request) }
            },
            confirm = { request -> launchOperation { persistence.confirm(request) } },
            cancel = ::cancel,
            acceptRecovery = ::acceptRecovery,
            declineRecovery = { launchOperation(persistence::declineRecovery) },
        )

    init {
        autosave.launchIn(viewModelScope)
        viewModelScope.launch {
            try {
                persistence.initializeRecovery()
            } finally {
                controller.synchronizeWithRuntime()
            }
        }
    }

    /**
     * Publishes any pending autosave capture immediately. Called from `MainActivity.onStop`; the
     * request runs on `viewModelScope`, so it outlives the activity instance.
     */
    fun flushAutosave() {
        autosave.flush()
    }

    private fun launchOperation(block: suspend () -> PersistenceRequestResult) {
        operationWorker.launch {
            try {
                block()
            } finally {
                controller.synchronizeWithRuntime()
            }
        }
    }

    /**
     * Recovery adoption is decided synchronously inside the runtime. A dirty document answers with a
     * confirmation request that the existing dialog already renders from the operation projection.
     */
    private fun acceptRecovery() {
        persistence.acceptRecovery()
        controller.synchronizeWithRuntime()
    }

    private fun cancel(handle: PersistenceOperationHandle) {
        when (persistence.cancel(handle)) {
            PersistenceCancellationResult.CancellationStarted -> operationWorker.cancel()

            PersistenceCancellationResult.Cancelled -> controller.synchronizeWithRuntime()

            PersistenceCancellationResult.Idle,
            PersistenceCancellationResult.Stale,
            PersistenceCancellationResult.TooLate,
            -> Unit
        }
    }

    internal companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    create(application)
                }
            }

        private fun create(application: Application): EditorRuntimeViewModel {
            val runtime = createEditorRuntime()
            val controller = EditorController.create(runtime)
            val pickerBroker = ProjectPickerBroker()
            val ioDispatcher = Dispatchers.IO.limitedParallelism(1)
            val projectStorage =
                AndroidProjectStorageAdapter.create(application.contentResolver, pickerBroker, ioDispatcher)
            val recoveryRecord =
                AndroidRecoveryRecordAdapter.create(
                    AtomicFile(File(application.noBackupFilesDir, RECOVERY_FILE_NAME)),
                    ioDispatcher,
                )
            val persistence =
                EditorPersistenceWorkflow.create(
                    runtime,
                    projectStorage,
                    recoveryRecord,
                    AndroidPngExportAdapter.create(application.contentResolver, pickerBroker, ioDispatcher),
                )
            return EditorRuntimeViewModel(runtime, controller, pickerBroker, persistence)
        }

        private const val RECOVERY_FILE_NAME: String = "nene-pixel-recovery-v1"
    }
}
