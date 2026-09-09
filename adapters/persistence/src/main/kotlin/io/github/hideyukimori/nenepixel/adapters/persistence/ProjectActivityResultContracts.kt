package io.github.hideyukimori.nenepixel.adapters.persistence

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure

public class CreateProjectDocumentContract : ActivityResultContract<String, ProjectPickerResult>() {
    public override fun createIntent(
        context: Context,
        input: String,
    ): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(PROJECT_MIME_TYPE)
            .putExtra(Intent.EXTRA_TITLE, input.withProjectExtension())

    public override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): ProjectPickerResult = parseProjectPickerResult(resultCode, intent)
}

public class OpenProjectDocumentContract : ActivityResultContract<Unit, ProjectPickerResult>() {
    public override fun createIntent(
        context: Context,
        input: Unit,
    ): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(PROJECT_MIME_TYPE)

    public override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): ProjectPickerResult = parseProjectPickerResult(resultCode, intent)
}

private const val PROJECT_MIME_TYPE: String = "application/octet-stream"
private const val PROJECT_EXTENSION: String = ".nenepixel"

private fun String.withProjectExtension(): String =
    if (endsWith(PROJECT_EXTENSION, ignoreCase = true)) this else this + PROJECT_EXTENSION

private fun parseProjectPickerResult(
    resultCode: Int,
    intent: Intent?,
): ProjectPickerResult =
    when (resultCode) {
        Activity.RESULT_CANCELED -> {
            ProjectPickerResult.Cancelled
        }

        Activity.RESULT_OK -> {
            val uri = intent?.data
            if (uri == null) {
                ProjectPickerResult.Failed(ProjectStorageFailure.InvalidPickerResult)
            } else {
                ProjectPickerResult.Selected(uri)
            }
        }

        else -> {
            ProjectPickerResult.Failed(ProjectStorageFailure.UnexpectedPickerResultCode)
        }
    }
