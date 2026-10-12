package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameDuration
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.R

/** Text is presentation mechanics; explicit confirmation alone sends one validated duration command. */
@Composable
internal fun FrameDurationDialog(
    row: FrameRowModel,
    ordinal: Int,
    callbacks: EditorFrameCallbacks,
    dismiss: () -> Unit,
) {
    var text by remember(row.id) { mutableStateOf(row.duration.value.toString()) }
    var problem by remember(row.id) { mutableStateOf<Int?>(null) }
    val confirm = {
        when (val result = frameDuration(text)) {
            FrameDurationInputResult.Invalid -> {
                problem = R.string.frame_duration_invalid
            }

            is FrameDurationInputResult.Valid -> {
                val notice = callbacks.onDuration(row.id, result.duration).frameNotice
                if (notice == null) dismiss() else problem = notice.resource()
            }
        }
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.frame_duration_title, ordinal)) },
        text = {
            FrameDurationFields(text, problem) {
                text = it
                problem = null
            }
        },
        confirmButton = {
            FrameDurationButton(R.string.frame_duration_confirm, confirm)
        },
        dismissButton = {
            FrameDurationButton(R.string.cancel, dismiss)
        },
    )
}

@Composable
private fun FrameDurationButton(
    label: Int,
    action: () -> Unit,
) {
    TextButton(
        onClick = action,
        modifier = Modifier.heightIn(min = LayerGeometry.HEADER_HEIGHT).editorDescription(label),
    ) {
        Text(stringResource(label))
    }
}

@Composable
private fun FrameDurationFields(
    text: String,
    problem: Int?,
    change: (String) -> Unit,
) {
    Column {
        OutlinedTextField(
            text,
            change,
            singleLine = true,
            label = { Text(stringResource(R.string.frame_duration_milliseconds)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.editorDescription(R.string.frame_duration_milliseconds),
        )
        Text(
            stringResource(
                R.string.frame_duration_bounds,
                FrameDuration.MIN_MILLISECONDS,
                FrameDuration.MAX_MILLISECONDS,
            ),
        )
        problem?.let { Text(stringResource(it)) }
    }
}

internal fun frameDuration(text: String): FrameDurationInputResult {
    val parsed = text.toIntOrNull()
    return if (parsed == null) {
        FrameDurationInputResult.Invalid
    } else {
        when (val result = FrameDuration.create(parsed)) {
            is DomainValueResult.Created -> FrameDurationInputResult.Valid(result.value)
            is DomainValueResult.Rejected -> FrameDurationInputResult.Invalid
        }
    }
}
