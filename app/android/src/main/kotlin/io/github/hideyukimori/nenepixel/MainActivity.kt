package io.github.hideyukimori.nenepixel

import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModelProvider
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectTransportPhase
import io.github.hideyukimori.nenepixel.presentation.compose.editor.NenePixelEditor

public class MainActivity : ComponentActivity() {
    private val editorModel: EditorRuntimeViewModel by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(
            this,
            EditorRuntimeViewModel.factory(application),
        )[EditorRuntimeViewModel::class.java]
    }
    private val createProjectLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(CreateProjectDocumentContract()) { result ->
            editorModel.pickerBroker.completeCreate(result)
        }
    private val openProjectLauncher: ActivityResultLauncher<Unit> =
        registerForActivityResult(OpenProjectDocumentContract()) { result ->
            editorModel.pickerBroker.completeOpen(result)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = editorModel
        setContent {
            val pickerRequest = model.pickerBroker.pendingRequest.collectAsState()
            LaunchedEffect(pickerRequest.value) {
                pickerRequest.value?.let { request -> launchPicker(request, model.pickerBroker) }
            }
            NenePixelEditor(
                renderStates = model.controller.renderStates,
                persistenceOperations = model.persistenceOperations,
                callbacks = model.controller.callbacks,
                persistenceCallbacks = model.persistenceCallbacks,
            )
        }
    }

    private fun launchPicker(
        request: ProjectPickerRequest,
        broker: ProjectPickerBroker,
    ) {
        if (!broker.claim(request)) return
        try {
            when (request) {
                is ProjectPickerRequest.Create -> createProjectLauncher.launch(request.suggestedName)
                is ProjectPickerRequest.Open -> openProjectLauncher.launch(Unit)
            }
        } catch (_: ActivityNotFoundException) {
            broker.failLaunch(
                request,
                ProjectStorageFailure.ProviderUnavailable(ProjectTransportPhase.PICKER_RESULT),
            )
        } catch (_: SecurityException) {
            broker.failLaunch(
                request,
                ProjectStorageFailure.PermissionDenied(ProjectTransportPhase.PICKER_RESULT),
            )
        } catch (_: RuntimeException) {
            broker.failLaunch(
                request,
                ProjectStorageFailure.ProviderUnavailable(ProjectTransportPhase.PICKER_RESULT),
            )
        }
    }
}
