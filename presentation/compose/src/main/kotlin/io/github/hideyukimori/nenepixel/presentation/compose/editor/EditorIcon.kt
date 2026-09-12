package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import io.github.hideyukimori.nenepixel.presentation.compose.R

internal enum class EditorIcon { Pencil, Eraser, Undo, Redo, Palette, File, Settings, Close }

@Composable
internal fun EditorSymbol(icon: EditorIcon) {
    val resource =
        when (icon) {
            EditorIcon.Pencil -> R.drawable.editor_pencil
            EditorIcon.Eraser -> R.drawable.editor_eraser
            EditorIcon.Undo -> R.drawable.editor_undo
            EditorIcon.Redo -> R.drawable.editor_redo
            EditorIcon.Palette -> R.drawable.editor_palette
            EditorIcon.File -> R.drawable.editor_file
            EditorIcon.Settings -> R.drawable.editor_settings
            EditorIcon.Close -> R.drawable.editor_close
        }
    Icon(painter = painterResource(resource), contentDescription = null)
}
