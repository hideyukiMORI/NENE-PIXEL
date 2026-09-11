package io.github.hideyukimori.nenepixel.adapters.persistence

import android.app.Activity
import android.content.Intent
import android.net.Uri
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure

/**
 * Canonical Storage Access Framework intents and typed result parsing for project documents.
 *
 * The app module wraps these in its own ActivityResultContract instances; the adapter itself
 * depends only on platform classes so its production graph stays free of androidx libraries.
 */
public object ProjectPickerIntents {
    public fun createDocument(request: DocumentCreationRequest): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(request.format.mimeType())
            .putExtra(Intent.EXTRA_TITLE, request.filename())

    public fun openDocument(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(PROJECT_MIME_TYPE)

    public fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): ProjectPickerResult =
        when (resultCode) {
            Activity.RESULT_CANCELED -> ProjectPickerResult.Cancelled
            Activity.RESULT_OK -> intent?.data.toSelection()
            else -> ProjectPickerResult.Failed(ProjectStorageFailure.UnexpectedPickerResultCode)
        }

    private fun Uri?.toSelection(): ProjectPickerResult =
        if (this == null) {
            ProjectPickerResult.Failed(ProjectStorageFailure.InvalidPickerResult)
        } else {
            ProjectPickerResult.Selected(this)
        }

    private fun DocumentCreationRequest.filename(): String {
        val extension =
            when (format) {
                DocumentOutputFormat.PROJECT -> ".nenepixel"
                DocumentOutputFormat.PNG -> ".png"
            }
        return if (suggestedName.endsWith(extension, ignoreCase = true)) suggestedName else suggestedName + extension
    }

    private fun DocumentOutputFormat.mimeType(): String =
        when (this) {
            DocumentOutputFormat.PROJECT -> PROJECT_MIME_TYPE
            DocumentOutputFormat.PNG -> "image/png"
        }

    private const val PROJECT_MIME_TYPE: String = "application/octet-stream"
}
