package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectTransportPhase
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatBytes
import kotlinx.coroutines.CancellationException
import java.io.InputStream

internal class ProjectDocumentReader(
    private val content: ProjectContentAccess,
) {
    fun read(
        location: ProjectLocation,
        purpose: ProjectReadPurpose,
    ): ProjectReadResult =
        when (val known = ProjectAccessResult.of(purpose.openPhase) { content.knownByteCount(location) }) {
            is ProjectAccessResult.Failed -> ProjectReadResult.Failed(known.failure)
            is ProjectAccessResult.Value -> openBounded(location, purpose, known.value)
        }

    private fun openBounded(
        location: ProjectLocation,
        purpose: ProjectReadPurpose,
        knownByteCount: Long?,
    ): ProjectReadResult =
        if (knownByteCount != null && knownByteCount > ProjectFormatBytes.MAX_FILE_BYTE_COUNT) {
            ProjectReadResult.Failed(ProjectStorageFailure.ResourceLimitExceeded)
        } else {
            open(location, purpose, knownByteCount)
        }

    private fun open(
        location: ProjectLocation,
        purpose: ProjectReadPurpose,
        knownByteCount: Long?,
    ): ProjectReadResult =
        when (val opened = ProjectAccessResult.of(purpose.openPhase) { content.openInput(location) }) {
            is ProjectAccessResult.Failed -> ProjectReadResult.Failed(opened.failure)
            is ProjectAccessResult.Value -> readOpened(opened.value, purpose, knownByteCount)
        }

    private fun readOpened(
        input: InputStream?,
        purpose: ProjectReadPurpose,
        knownByteCount: Long?,
    ): ProjectReadResult =
        if (input == null) {
            ProjectReadResult.Failed(ProjectStorageFailure.ProviderUnavailable(purpose.openPhase))
        } else {
            readStream(input, purpose, knownByteCount)
        }

    private fun readStream(
        input: InputStream,
        purpose: ProjectReadPurpose,
        knownByteCount: Long?,
    ): ProjectReadResult {
        val attempt =
            try {
                ProjectReadAttempt.Completed(bounded(input, knownByteCount, purpose.readPhase))
            } catch (cancelled: CancellationException) {
                ProjectReadAttempt.Cancelled(cancelled)
            }
        val closeFailure = ProjectAccessResult.closeFailure(input, purpose.closePhase)
        return when (attempt) {
            is ProjectReadAttempt.Cancelled -> throw attempt.cancellation
            is ProjectReadAttempt.Completed -> normalize(attempt.result, closeFailure)
        }
    }

    private fun normalize(
        result: ProjectReadResult,
        closeFailure: ProjectStorageFailure?,
    ): ProjectReadResult =
        when {
            result is ProjectReadResult.Failed -> result
            closeFailure != null -> ProjectReadResult.Failed(closeFailure)
            else -> result
        }

    private fun bounded(
        input: InputStream,
        knownByteCount: Long?,
        phase: ProjectTransportPhase,
    ): ProjectReadResult =
        when (val result = ProjectAccessResult.of(phase) { BoundedStreamReader.read(input, knownByteCount, MAX) }) {
            is ProjectAccessResult.Failed -> ProjectReadResult.Failed(result.failure)
            is ProjectAccessResult.Value -> mapBoundedRead(result.value)
        }

    private fun mapBoundedRead(result: BoundedReadResult): ProjectReadResult =
        when (result) {
            is BoundedReadResult.Bytes -> {
                ProjectReadResult.Bytes(result.value)
            }

            BoundedReadResult.ResourceLimitExceeded -> {
                ProjectReadResult.Failed(ProjectStorageFailure.ResourceLimitExceeded)
            }

            BoundedReadResult.ZeroProgress -> {
                ProjectReadResult.Failed(ProjectStorageFailure.ZeroProgress)
            }

            BoundedReadResult.PrematureEnd -> {
                ProjectReadResult.Failed(ProjectStorageFailure.PrematureEnd)
            }
        }

    private companion object {
        const val MAX: Int = ProjectFormatBytes.MAX_FILE_BYTE_COUNT
    }
}

internal sealed interface ProjectReadResult {
    data class Bytes(
        val value: ByteArray,
    ) : ProjectReadResult

    data class Failed(
        val failure: ProjectStorageFailure,
    ) : ProjectReadResult
}

internal enum class ProjectReadPurpose(
    val openPhase: ProjectTransportPhase,
    val readPhase: ProjectTransportPhase,
    val closePhase: ProjectTransportPhase,
) {
    SOURCE(
        ProjectTransportPhase.SOURCE_OPEN,
        ProjectTransportPhase.SOURCE_READ,
        ProjectTransportPhase.SOURCE_CLOSE,
    ),
    READ_BACK(
        ProjectTransportPhase.READ_BACK_OPEN,
        ProjectTransportPhase.READ_BACK_READ,
        ProjectTransportPhase.READ_BACK_CLOSE,
    ),
}

private sealed interface ProjectReadAttempt {
    data class Completed(
        val result: ProjectReadResult,
    ) : ProjectReadAttempt

    data class Cancelled(
        val cancellation: CancellationException,
    ) : ProjectReadAttempt
}
