package io.github.hideyukimori.nenepixel.adapters.persistence

import android.content.ContentResolver
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportSourceRejection
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure
import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Picks one PNG file through SAF and reads it into an [ImportRaster] (ADR 0033). */
public class AndroidPngImportAdapter private constructor(
    private val reader: ProjectDocumentReader,
    private val picker: ProjectPickerAccess,
    private val ioDispatcher: CoroutineDispatcher,
) : PngImportPort {
    override suspend fun pick(): PngImportOutcome =
        when (val result = picker.openDocument(DocumentOpenRequest(DocumentOutputFormat.PNG))) {
            is InternalPickerResult.Selected -> pickSelected(result.location)
            InternalPickerResult.Cancelled -> PngImportOutcome.Cancelled
            is InternalPickerResult.Failed -> PngImportOutcome.Failed(result.failure)
        }

    private suspend fun pickSelected(location: ProjectLocation): PngImportOutcome =
        withContext(ioDispatcher) {
            when (val read = reader.read(location, ProjectReadPurpose.SOURCE)) {
                is ProjectReadResult.Bytes -> fromBytes(read.value)
                is ProjectReadResult.Failed -> fromReadFailure(read.failure)
            }
        }

    private fun fromBytes(bytes: ByteArray): PngImportOutcome =
        if (bytes.size > PngImportLimits.MAX_ENCODED_BYTE_COUNT) {
            rejected(PngImportSourceRejection.TooManyBytes)
        } else {
            fromDecoded(PngImportDecoder.decode(bytes))
        }

    private fun fromDecoded(decoded: PngImportDecodeResult): PngImportOutcome =
        when (decoded) {
            is PngImportDecodeResult.Decoded -> {
                fromCreated(ImportRaster.create(decoded.width, decoded.height, decoded.packedRgba8888))
            }

            PngImportDecodeResult.TooManyPixels -> {
                rejected(PngImportSourceRejection.TooManyPixels)
            }

            PngImportDecodeResult.Unsupported -> {
                rejected(PngImportSourceRejection.Unsupported)
            }
        }

    private fun fromCreated(created: DomainValueResult<ImportRaster>): PngImportOutcome =
        when (created) {
            is DomainValueResult.Created -> PngImportOutcome.Picked(created.value)
            is DomainValueResult.Rejected -> rejected(PngImportSourceRejection.Unsupported)
        }

    private fun fromReadFailure(failure: ProjectStorageFailure): PngImportOutcome =
        if (failure == ProjectStorageFailure.ResourceLimitExceeded) {
            rejected(PngImportSourceRejection.TooManyBytes)
        } else {
            PngImportOutcome.Failed(failure)
        }

    private fun rejected(reason: PngImportSourceRejection): PngImportOutcome = PngImportOutcome.Rejected(reason)

    public companion object {
        public fun create(
            contentResolver: ContentResolver,
            picker: ProjectDocumentPicker,
            ioDispatcher: CoroutineDispatcher,
        ): PngImportPort =
            create(
                ContentResolverProjectContentAccess(contentResolver),
                AndroidProjectPickerAccess(picker),
                ioDispatcher,
            )

        internal fun create(
            content: ProjectContentAccess,
            picker: ProjectPickerAccess,
            ioDispatcher: CoroutineDispatcher,
        ): PngImportPort =
            AndroidPngImportAdapter(
                ProjectDocumentReader(content, PngImportLimits.MAX_PROBE_BYTE_COUNT),
                picker,
                ioDispatcher,
            )
    }
}
