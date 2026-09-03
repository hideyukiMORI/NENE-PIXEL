package io.github.hideyukimori.nenepixel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import io.github.hideyukimori.nenepixel.presentation.compose.editor.NenePixelEditor

public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller =
            ViewModelProvider(this, EditorRuntimeViewModel.factory)[EditorRuntimeViewModel::class.java]
                .controller
        setContent {
            NenePixelEditor(
                initialState = controller.renderState,
                callbacks = controller.callbacks,
            )
        }
    }
}
