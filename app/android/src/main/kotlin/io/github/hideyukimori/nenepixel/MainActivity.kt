package io.github.hideyukimori.nenepixel

import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModelProvider
import io.github.hideyukimori.nenepixel.adapters.persistence.DocumentCreationRequest
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectTransportPhase
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorTheme
import io.github.hideyukimori.nenepixel.presentation.compose.editor.NenePixelEditor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

public class MainActivity : ComponentActivity() {
    private val editorModel: EditorRuntimeViewModel by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(
            this,
            EditorRuntimeViewModel.factory(application),
        )[EditorRuntimeViewModel::class.java]
    }
    private val languageModel: AppLanguageViewModel by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(this, AppLanguageViewModel.factory(application))[AppLanguageViewModel::class.java]
    }
    private val createProjectLauncher: ActivityResultLauncher<DocumentCreationRequest> =
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
        applySystemBarTheme(model.controller.renderState.appearance.theme)
        setContent {
            LaunchedEffect(model) {
                model.controller.renderStates.map { it.appearance.theme }.distinctUntilChanged().collect(
                    ::applySystemBarTheme,
                )
            }
            val pickerRequest = model.pickerBroker.pendingRequest.collectAsState()
            LaunchedEffect(pickerRequest.value) {
                pickerRequest.value?.let { request -> launchPicker(request, model.pickerBroker) }
            }
            LocalizedAppLanguage(languageModel.controller.controls) {
                NenePixelEditor(
                    renderStates = model.controller.renderStates,
                    persistenceOperations = model.persistenceOperations,
                    autosaveStates = model.autosaveStates,
                    callbacks = model.controller.callbacks,
                    persistenceCallbacks = model.persistenceCallbacks,
                    language = languageModel.controller.controls,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        languageModel.controller.refresh()
    }

    /**
     * ADR 0018 places the autosave lifecycle flush on `ON_STOP`, the last reliable event before a
     * background process death. The request itself runs on the retained ViewModel scope.
     */
    override fun onStop() {
        super.onStop()
        editorModel.flushAutosave()
    }

    private fun applySystemBarTheme(theme: EditorTheme) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style =
            when (theme) {
                EditorTheme.Dark -> SystemBarStyle.dark(transparent)
                EditorTheme.Light -> SystemBarStyle.light(transparent, transparent)
            }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private fun launchPicker(
        request: ProjectPickerRequest,
        broker: ProjectPickerBroker,
    ) {
        if (!broker.claim(request)) return
        try {
            when (request) {
                is ProjectPickerRequest.Create -> createProjectLauncher.launch(request.creation)
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
