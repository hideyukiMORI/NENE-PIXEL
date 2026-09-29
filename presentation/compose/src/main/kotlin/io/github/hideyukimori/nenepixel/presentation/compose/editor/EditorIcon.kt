package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import io.github.hideyukimori.nenepixel.presentation.compose.R

/** One editor symbol and the vector drawable that draws it. */
internal enum class EditorIcon(
    val drawable: Int,
) {
    Pencil(R.drawable.editor_pencil),
    Eraser(R.drawable.editor_eraser),
    Undo(R.drawable.editor_undo),
    Redo(R.drawable.editor_redo),
    Palette(R.drawable.editor_palette),
    ActualSize(R.drawable.editor_actual_size),
    File(R.drawable.editor_file),
    Settings(R.drawable.editor_settings),
    Close(R.drawable.editor_close),
    Eyedropper(R.drawable.editor_eyedropper),
    Layers(R.drawable.editor_layers),
    Visible(R.drawable.editor_visible),
    Hidden(R.drawable.editor_hidden),
    Add(R.drawable.editor_add),
    More(R.drawable.editor_more),
}

@Composable
internal fun EditorSymbol(icon: EditorIcon) {
    Icon(painter = painterResource(icon.drawable), contentDescription = null)
}
