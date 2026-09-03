package io.github.hideyukimori.nenepixel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorController

internal class EditorRuntimeViewModel private constructor(
    val runtime: EditorRuntime,
) : ViewModel() {
    val controller: EditorController = EditorController.create(runtime)

    companion object {
        val factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    EditorRuntimeViewModel(createEditorRuntime())
                }
            }
    }
}
