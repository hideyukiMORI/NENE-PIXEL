package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguage
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguageControls
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguageSettings
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Invoked on the host main lane; suspended I/O cannot admit a second operation. */
internal class AppLanguageController(
    private val storage: AppLanguageStorage,
    private val scope: CoroutineScope,
) {
    private val mutableSettings = MutableStateFlow(AppLanguageSettings(AppLanguage.System, AppLanguageStatus.Loading))
    val settings = mutableSettings.asStateFlow()
    val controls = AppLanguageControls(settings, ::select, ::retry)
    private var failedSelection: AppLanguage? = null
    private var refreshAfterOperation = false
    private var operation: LanguageOperation = LanguageOperation.Idle

    fun refresh() {
        if (operation != LanguageOperation.Idle) {
            refreshAfterOperation = true
            return
        }
        operation = LanguageOperation.Read
        scope.launch {
            mutableSettings.value =
                when (val result = storage.read()) {
                    is AppLanguageRead.Loaded -> AppLanguageSettings(result.language, AppLanguageStatus.Ready)
                    AppLanguageRead.Failed -> settings.value.copy(status = AppLanguageStatus.ReadFailed)
                }
            completeOperation()
        }
    }

    fun select(language: AppLanguage) {
        if (operation != LanguageOperation.Idle) return
        if (settings.value.status == AppLanguageStatus.Ready && settings.value.selection == language) return
        failedSelection = language
        operation = LanguageOperation.Write(language)
        mutableSettings.value = settings.value.copy(status = AppLanguageStatus.Applying)
        scope.launch {
            mutableSettings.value =
                when (storage.write(language)) {
                    AppLanguageWrite.Saved -> {
                        failedSelection = null
                        AppLanguageSettings(language, AppLanguageStatus.Ready)
                    }

                    AppLanguageWrite.Failed -> {
                        settings.value.copy(status = AppLanguageStatus.WriteFailed)
                    }
                }
            completeOperation()
        }
    }

    fun retry() {
        val selection = failedSelection
        if (selection != null &&
            settings.value.status == AppLanguageStatus.WriteFailed
        ) {
            select(selection)
        } else {
            refresh()
        }
    }

    private fun completeOperation() {
        operation = LanguageOperation.Idle
        if (refreshAfterOperation) {
            refreshAfterOperation = false
            refresh()
        }
    }
}

private sealed interface LanguageOperation {
    data object Idle : LanguageOperation

    data object Read : LanguageOperation

    data class Write(
        val selection: AppLanguage,
    ) : LanguageOperation
}
