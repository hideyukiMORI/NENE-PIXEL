package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorTheme
import io.github.hideyukimori.nenepixel.presentation.compose.R

@Composable
internal fun LanguageLoadingScreen() {
    val colors = EditorTheme.Dark.colorScheme()
    Surface(Modifier.fillMaxSize(), color = colors.background, contentColor = colors.onBackground) {
        Box(contentAlignment = Alignment.Center) { Text(stringResource(R.string.language_loading)) }
    }
}
