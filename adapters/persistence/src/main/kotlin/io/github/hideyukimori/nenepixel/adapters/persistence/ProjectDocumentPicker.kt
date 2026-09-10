package io.github.hideyukimori.nenepixel.adapters.persistence

import android.net.Uri
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure

public interface ProjectDocumentPicker {
    public suspend fun createDocument(suggestedName: String): ProjectPickerResult

    public suspend fun openDocument(): ProjectPickerResult
}

public sealed interface ProjectPickerResult {
    public data class Selected(
        public val uri: Uri,
    ) : ProjectPickerResult

    public data object Cancelled : ProjectPickerResult

    public data class Failed(
        public val failure: ProjectStorageFailure,
    ) : ProjectPickerResult
}

internal interface ProjectPickerAccess {
    suspend fun createDocument(suggestedName: String): InternalPickerResult

    suspend fun openDocument(): InternalPickerResult
}

internal class AndroidProjectPickerAccess(
    private val picker: ProjectDocumentPicker,
) : ProjectPickerAccess {
    override suspend fun createDocument(suggestedName: String): InternalPickerResult =
        picker.createDocument(suggestedName).toInternal()

    override suspend fun openDocument(): InternalPickerResult = picker.openDocument().toInternal()

    private fun ProjectPickerResult.toInternal(): InternalPickerResult =
        when (this) {
            is ProjectPickerResult.Selected -> InternalPickerResult.Selected(UriProjectLocation(uri))
            ProjectPickerResult.Cancelled -> InternalPickerResult.Cancelled
            is ProjectPickerResult.Failed -> InternalPickerResult.Failed(failure)
        }
}

internal sealed interface InternalPickerResult {
    data class Selected(
        val location: ProjectLocation,
    ) : InternalPickerResult

    data object Cancelled : InternalPickerResult

    data class Failed(
        val failure: ProjectStorageFailure,
    ) : InternalPickerResult
}
