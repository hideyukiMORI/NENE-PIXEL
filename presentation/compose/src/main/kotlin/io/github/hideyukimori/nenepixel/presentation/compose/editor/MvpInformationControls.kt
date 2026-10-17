package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.presentation.compose.R

@Composable
internal fun MvpInformationControls() {
    val uriHandler = LocalUriHandler.current
    var visible by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { visible = true }, modifier = Modifier.editorDescription(R.string.mvp_information)) {
        Text(stringResource(R.string.mvp_information))
    }
    if (visible) {
        MvpInformationDialog(openPrivacy = { uriHandler.openUri(PRIVACY_STATEMENT_URL) }) { visible = false }
    }
}

@Composable
private fun MvpInformationDialog(
    openPrivacy: () -> Unit,
    dismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        onDismissRequest = dismiss,
        title = {
            Text(stringResource(R.string.mvp_information), modifier = Modifier.testTag("editor_mvp_information_title"))
        },
        text = { MvpInformationContent(openPrivacy) },
        confirmButton = {
            TextButton(onClick = dismiss, modifier = Modifier.editorDescription(R.string.close_information)) {
                Text(stringResource(R.string.close_information))
            }
        },
    )
}

@Composable
private fun MvpInformationContent(openPrivacy: () -> Unit) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        InformationSection(
            R.string.mvp_scope_title,
            stringResource(R.string.mvp_scope, PixelLimits.MIN_CANVAS_AXIS, PixelLimits.MAX_CANVAS_AXIS),
        )
        InformationSection(R.string.mvp_save_title, stringResource(R.string.mvp_save))
        InformationSection(
            R.string.mvp_history_title,
            stringResource(R.string.mvp_history, PixelLimits.MAX_HISTORY_ENTRIES),
        )
        InformationSection(R.string.mvp_recovery_title, stringResource(R.string.mvp_recovery))
        InformationSection(R.string.mvp_png_title, stringResource(R.string.mvp_png))
        InformationSection(R.string.mvp_compatibility_title, stringResource(R.string.mvp_compatibility))
        InformationSection(R.string.mvp_privacy_title, stringResource(R.string.mvp_privacy))
        InformationSection(R.string.mvp_private_data_title, stringResource(R.string.mvp_private_data))
        InformationSection(R.string.mvp_external_data_title, stringResource(R.string.mvp_external_data))
        PrivacyStatementLink(openPrivacy)
    }
}

@Composable
private fun PrivacyStatementLink(openPrivacy: () -> Unit) {
    var linkUnavailable by rememberSaveable { mutableStateOf(false) }
    TextButton(
        onClick = {
            linkUnavailable =
                try {
                    openPrivacy()
                    false
                } catch (_: IllegalArgumentException) {
                    true
                }
        },
        modifier = Modifier.editorDescription(R.string.mvp_privacy_details),
    ) {
        Text(stringResource(R.string.mvp_privacy_details))
    }
    if (linkUnavailable) {
        Text(
            stringResource(R.string.mvp_privacy_link_unavailable),
            modifier = Modifier.editorDescription(R.string.mvp_privacy_link_unavailable),
        )
    }
}

private const val PRIVACY_STATEMENT_URL =
    "https://github.com/hideyukiMORI/NENE-PIXEL/blob/main/docs/PRIVACY_AND_DATA_HANDLING.md"

@Composable
private fun InformationSection(
    title: Int,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.editorDescription(title),
        )
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
