package io.github.hideyukimori.nenepixel

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguage
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguageControls
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguageSettings
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguageStatus
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppVersionDisplay
import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorController
import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorSettingsInputs
import kotlinx.coroutines.flow.MutableStateFlow

/** Existing internal app construction seam, actual retained worker and scheduler (#247). */
internal class EditorRecoveryFaultFixture(
    case: String,
) {
    val recovery = RecoveryFaultFixture(case)
    private val controller = EditorController.create(recovery.runtime)
    lateinit var model: EditorRuntimeViewModel
        private set
    val attached: Boolean get() = ::model.isInitialized
    val settings =
        EditorSettingsInputs(
            AppLanguageControls(
                MutableStateFlow(AppLanguageSettings(AppLanguage.System, AppLanguageStatus.Ready)),
                {},
                {},
            ),
            AppVersionDisplay.Available("synthetic-recovery-fault", 1L),
        )

    fun attach(activity: ComponentActivity) {
        controller.synchronizeWithRuntime()
        val factory =
            viewModelFactory {
                initializer {
                    EditorRuntimeViewModel(recovery.runtime, controller, ProjectPickerBroker(), recovery.workflow)
                }
            }
        model = ViewModelProvider(activity, factory)[EditorRuntimeViewModel::class.java]
    }

    fun captureAndFlush(fault: RecoveryFaultPhase) {
        recovery.captureSecond(fault)
        controller.synchronizeWithRuntime()
        model.flushAutosave()
    }
}
