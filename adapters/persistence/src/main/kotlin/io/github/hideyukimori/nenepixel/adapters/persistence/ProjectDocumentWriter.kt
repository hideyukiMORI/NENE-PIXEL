package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.PartialOutputCleanup
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectTransportPhase
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatBytes
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatResult
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatV1Codec
import kotlinx.coroutines.CancellationException
import java.io.OutputStream

internal class ProjectDocumentWriter(
    private val content: ProjectContentAccess,
    private val reader: ProjectDocumentReader,
) {
    fun writeAndVerify(
        location: ProjectLocation,
        expectedBytes: ByteArray,
    ): ProjectStorageFailure? = writeBytes(location, expectedBytes) ?: verify(location, expectedBytes)

    fun deleteOutput(location: ProjectLocation): PartialOutputCleanup =
        try {
            if (content.delete(location) > 0) PartialOutputCleanup.DELETED else PartialOutputCleanup.DELETE_FAILED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Content providers may surface undocumented checked or runtime failures here.
            PartialOutputCleanup.DELETE_FAILED
        }

    private fun writeBytes(
        location: ProjectLocation,
        bytes: ByteArray,
    ): ProjectStorageFailure? =
        when (val opened = ProjectAccessResult.of(OPEN_PHASE) { content.openOutput(location) }) {
            is ProjectAccessResult.Failed -> opened.failure
            is ProjectAccessResult.Value -> writeOpened(opened.value, bytes)
        }

    private fun writeOpened(
        output: OutputStream?,
        bytes: ByteArray,
    ): ProjectStorageFailure? =
        if (output == null) {
            ProjectStorageFailure.ProviderUnavailable(OPEN_PHASE)
        } else {
            writeStream(output, bytes)
        }

    private fun writeStream(
        output: OutputStream,
        bytes: ByteArray,
    ): ProjectStorageFailure? {
        val attempt =
            try {
                ProjectWriteAttempt.Completed(writeAll(output, bytes))
            } catch (cancelled: CancellationException) {
                ProjectWriteAttempt.Cancelled(cancelled)
            }
        val closeFailure = ProjectAccessResult.closeFailure(output, ProjectTransportPhase.DESTINATION_CLOSE)
        return when (attempt) {
            is ProjectWriteAttempt.Cancelled -> throw attempt.cancellation
            is ProjectWriteAttempt.Completed -> attempt.failure ?: closeFailure
        }
    }

    private fun writeAll(
        output: OutputStream,
        bytes: ByteArray,
    ): ProjectStorageFailure? =
        when (val result = ProjectAccessResult.of(ProjectTransportPhase.DESTINATION_WRITE) { output.write(bytes) }) {
            is ProjectAccessResult.Value -> null
            is ProjectAccessResult.Failed -> result.failure
        }

    private fun verify(
        location: ProjectLocation,
        expectedBytes: ByteArray,
    ): ProjectStorageFailure? =
        when (val read = reader.read(location, ProjectReadPurpose.READ_BACK)) {
            is ProjectReadResult.Failed -> read.failure
            is ProjectReadResult.Bytes -> validateReadBack(read.value, expectedBytes)
        }

    private fun validateReadBack(
        actualBytes: ByteArray,
        expectedBytes: ByteArray,
    ): ProjectStorageFailure? =
        if (actualBytes.contentEquals(expectedBytes)) {
            validateDecodable(actualBytes)
        } else {
            ProjectStorageFailure.ReadBackMismatch
        }

    private fun validateDecodable(bytes: ByteArray): ProjectStorageFailure? =
        when (val carrier = ProjectFormatBytes.create(bytes)) {
            is ProjectFormatResult.Rejected -> {
                ProjectStorageRejectionMapper.map(carrier.rejection)
            }

            is ProjectFormatResult.Accepted -> {
                when (val decoded = ProjectFormatV1Codec.decode(carrier.value)) {
                    is ProjectFormatResult.Accepted -> null
                    is ProjectFormatResult.Rejected -> ProjectStorageRejectionMapper.map(decoded.rejection)
                }
            }
        }

    private companion object {
        val OPEN_PHASE: ProjectTransportPhase = ProjectTransportPhase.DESTINATION_OPEN
    }
}

private sealed interface ProjectWriteAttempt {
    data class Completed(
        val failure: ProjectStorageFailure?,
    ) : ProjectWriteAttempt

    data class Cancelled(
        val cancellation: CancellationException,
    ) : ProjectWriteAttempt
}
