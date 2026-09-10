package io.github.hideyukimori.nenepixel.adapters.persistence

import android.content.ContentResolver
import io.github.hideyukimori.nenepixel.core.application.persistence.PartialOutputCleanup
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectLoadOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectSaveOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStoragePort
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatBytes
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatResult
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatV1Codec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

public class AndroidProjectStorageAdapter private constructor(
    private val reader: ProjectDocumentReader,
    private val writer: ProjectDocumentWriter,
    private val picker: ProjectPickerAccess,
    private val ioDispatcher: CoroutineDispatcher,
) : ProjectStoragePort {
    override suspend fun save(document: DocumentState): ProjectSaveOutcome =
        when (val prepared = withContext(ioDispatcher) { encodeAndValidate(document) }) {
            is ProjectPreparation.Prepared -> {
                pickDestination(prepared.bytes)
            }

            ProjectPreparation.Invalid -> {
                ProjectSaveOutcome.Failed(
                    ProjectStorageFailure.InvalidProject,
                    PartialOutputCleanup.NOT_NEEDED,
                )
            }
        }

    override suspend fun load(): ProjectLoadOutcome =
        when (val result = picker.openDocument()) {
            is InternalPickerResult.Selected -> loadSelected(result.location)
            InternalPickerResult.Cancelled -> ProjectLoadOutcome.Cancelled
            is InternalPickerResult.Failed -> ProjectLoadOutcome.Failed(result.failure)
        }

    private suspend fun pickDestination(expectedBytes: ByteArray): ProjectSaveOutcome =
        when (val result = picker.createDocument(DEFAULT_PROJECT_NAME)) {
            is InternalPickerResult.Selected -> {
                saveSelected(result.location, expectedBytes)
            }

            InternalPickerResult.Cancelled -> {
                ProjectSaveOutcome.Cancelled
            }

            is InternalPickerResult.Failed -> {
                ProjectSaveOutcome.Failed(result.failure, PartialOutputCleanup.NOT_NEEDED)
            }
        }

    private suspend fun saveSelected(
        location: ProjectLocation,
        expectedBytes: ByteArray,
    ): ProjectSaveOutcome =
        try {
            withContext(ioDispatcher) {
                writeVerified(location, expectedBytes)
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable + ioDispatcher) {
                writer.deleteOutput(location)
            }
            throw cancelled
        }

    private fun writeVerified(
        location: ProjectLocation,
        expectedBytes: ByteArray,
    ): ProjectSaveOutcome {
        val failure = writer.writeAndVerify(location, expectedBytes)
        return if (failure == null) {
            ProjectSaveOutcome.Saved
        } else {
            ProjectSaveOutcome.Failed(failure, writer.deleteOutput(location))
        }
    }

    private suspend fun loadSelected(location: ProjectLocation): ProjectLoadOutcome =
        withContext(ioDispatcher) {
            when (val read = reader.read(location, ProjectReadPurpose.SOURCE)) {
                is ProjectReadResult.Bytes -> decodeLoaded(read.value)
                is ProjectReadResult.Failed -> ProjectLoadOutcome.Failed(read.failure)
            }
        }

    private fun decodeLoaded(bytes: ByteArray): ProjectLoadOutcome =
        when (val carrier = ProjectFormatBytes.create(bytes)) {
            is ProjectFormatResult.Accepted -> {
                when (val decoded = ProjectFormatV1Codec.decode(carrier.value)) {
                    is ProjectFormatResult.Accepted -> {
                        ProjectLoadOutcome.Loaded(decoded.value)
                    }

                    is ProjectFormatResult.Rejected -> {
                        ProjectLoadOutcome.Failed(ProjectStorageRejectionMapper.map(decoded.rejection))
                    }
                }
            }

            is ProjectFormatResult.Rejected -> {
                ProjectLoadOutcome.Failed(ProjectStorageRejectionMapper.map(carrier.rejection))
            }
        }

    private fun encodeAndValidate(document: DocumentState): ProjectPreparation {
        val encoded = ProjectFormatV1Codec.encode(document)
        return when (val decoded = ProjectFormatV1Codec.decode(encoded)) {
            is ProjectFormatResult.Accepted -> {
                if (decoded.value == document) {
                    ProjectPreparation.Prepared(encoded.copyBytes())
                } else {
                    ProjectPreparation.Invalid
                }
            }

            is ProjectFormatResult.Rejected -> {
                ProjectPreparation.Invalid
            }
        }
    }

    public companion object {
        private const val DEFAULT_PROJECT_NAME: String = "nene-pixel.nenepixel"

        public fun create(
            contentResolver: ContentResolver,
            picker: ProjectDocumentPicker,
            ioDispatcher: CoroutineDispatcher,
        ): ProjectStoragePort =
            create(
                ContentResolverProjectContentAccess(contentResolver),
                AndroidProjectPickerAccess(picker),
                ioDispatcher,
            )

        internal fun create(
            content: ProjectContentAccess,
            picker: ProjectPickerAccess,
            ioDispatcher: CoroutineDispatcher,
        ): ProjectStoragePort {
            val reader = ProjectDocumentReader(content)
            return AndroidProjectStorageAdapter(reader, ProjectDocumentWriter(content, reader), picker, ioDispatcher)
        }
    }
}

private sealed interface ProjectPreparation {
    data class Prepared(
        val bytes: ByteArray,
    ) : ProjectPreparation

    data object Invalid : ProjectPreparation
}
