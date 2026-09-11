package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.PartialOutputCleanup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal class FreshDocumentOutput(
    private val writer: FreshDocumentWriter,
    private val picker: ProjectPickerAccess,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun write(
        request: DocumentCreationRequest,
        expectedBytes: ByteArray,
    ): FreshOutputResult =
        when (val result = picker.createDocument(request)) {
            is InternalPickerResult.Selected -> {
                saveSelected(result.location, expectedBytes)
            }

            InternalPickerResult.Cancelled -> {
                coroutineContext.ensureActive()
                FreshOutputResult.Cancelled
            }

            is InternalPickerResult.Failed -> {
                coroutineContext.ensureActive()
                FreshOutputResult.Failed(result.failure, PartialOutputCleanup.NOT_NEEDED)
            }
        }

    private suspend fun saveSelected(
        location: ProjectLocation,
        expectedBytes: ByteArray,
    ): FreshOutputResult =
        try {
            coroutineContext.ensureActive()
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
    ): FreshOutputResult {
        val failure = writer.writeAndVerify(location, expectedBytes)
        return if (failure == null) {
            FreshOutputResult.Written
        } else {
            FreshOutputResult.Failed(failure, writer.deleteOutput(location))
        }
    }
}
