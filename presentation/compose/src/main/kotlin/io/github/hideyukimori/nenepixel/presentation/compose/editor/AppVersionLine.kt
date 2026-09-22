package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.hideyukimori.nenepixel.presentation.compose.R

/**
 * The last line of the settings sheet. It states which build is installed and carries no control,
 * so the always-visible drawing surface keeps only its direct-manipulation controls.
 */
@Composable
internal fun AppVersionLine(version: AppVersionDisplay) {
    Text(
        text = version.text(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("editor_app_version"),
    )
}

internal fun AppVersionDisplay.labelResource(): Int =
    when (this) {
        is AppVersionDisplay.Available -> if (versionCode == null) R.string.app_version_name else R.string.app_version
        AppVersionDisplay.Unavailable -> R.string.app_version_unavailable
    }

@Composable
private fun AppVersionDisplay.text(): String {
    val resource = labelResource()
    return when (this) {
        is AppVersionDisplay.Available -> {
            if (versionCode == null) {
                stringResource(resource, versionName)
            } else {
                stringResource(resource, versionName, versionCode)
            }
        }

        AppVersionDisplay.Unavailable -> {
            stringResource(resource)
        }
    }
}
