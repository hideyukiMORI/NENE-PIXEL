package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.presentation.compose.R

@Composable
internal fun LanguageChoices(
    controls: AppLanguageControls,
    cancelPreview: () -> Unit,
) {
    val settings by controls.settings.collectAsState()
    Text(stringResource(R.string.language), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppLanguage.entries.forEach { language ->
            val label = language.labelResource()
            FilterChip(
                selected = settings.selection == language,
                enabled = settings.status != AppLanguageStatus.Applying && settings.status != AppLanguageStatus.Loading,
                onClick = {
                    cancelPreview()
                    controls.select(language)
                },
                label = { Text(stringResource(label)) },
                modifier = Modifier.heightIn(min = 48.dp).editorDescription(label),
            )
        }
    }
    Text(stringResource(settings.status.messageResource()), style = MaterialTheme.typography.bodySmall)
    if (settings.status == AppLanguageStatus.ReadFailed || settings.status == AppLanguageStatus.WriteFailed) {
        TextButton(onClick = controls.retry, modifier = Modifier.editorDescription(R.string.retry)) {
            Text(stringResource(R.string.retry))
        }
    }
}

private fun AppLanguage.labelResource(): Int =
    when (this) {
        AppLanguage.System -> R.string.language_system
        AppLanguage.English -> R.string.language_english
        AppLanguage.Japanese -> R.string.language_japanese
        AppLanguage.SimplifiedChinese -> R.string.language_chinese
    }

private fun AppLanguageStatus.messageResource(): Int =
    when (this) {
        AppLanguageStatus.Loading -> R.string.language_loading
        AppLanguageStatus.Ready -> R.string.language_hint
        AppLanguageStatus.Applying -> R.string.language_applying
        AppLanguageStatus.ReadFailed -> R.string.language_read_failed
        AppLanguageStatus.WriteFailed -> R.string.language_write_failed
    }
