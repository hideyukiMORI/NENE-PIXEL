package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImageOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImagePort
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImageSourceRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Picks one source picture through SAF and turns it into a [ReferenceImage] (ADR 0032). */
public class AndroidReferenceImageAdapter private constructor(
    private val reader: ProjectDocumentReader,
    private val picker: ProjectPickerAccess,
    private val ioDispatcher: CoroutineDispatcher,
    private val decoder: ReferenceImageDecoder,
) : ReferenceImagePort {
    override suspend fun pick(): ReferenceImageOutcome =
        when (val result = picker.openDocument(DocumentOpenRequest(DocumentOutputFormat.REFERENCE_IMAGE))) {
            is InternalPickerResult.Selected -> pickSelected(result.location)
            InternalPickerResult.Cancelled -> ReferenceImageOutcome.Cancelled
            is InternalPickerResult.Failed -> ReferenceImageOutcome.Failed(result.failure)
        }

    private suspend fun pickSelected(location: ProjectLocation): ReferenceImageOutcome =
        withContext(ioDispatcher) {
            when (val read = reader.read(location, ProjectReadPurpose.SOURCE)) {
                is ProjectReadResult.Bytes -> fromBytes(read.value)
                is ProjectReadResult.Failed -> fromReadFailure(read.failure)
            }
        }

    private fun fromBytes(bytes: ByteArray): ReferenceImageOutcome =
        if (bytes.size > ReferenceImageLimits.MAX_ENCODED_BYTE_COUNT) {
            rejected(ReferenceImageSourceRejection.TooManyBytes)
        } else {
            fromDecoded(decoder.decode(bytes))
        }

    private fun fromDecoded(decoded: ReferenceImageDecodeResult): ReferenceImageOutcome =
        when (decoded) {
            is ReferenceImageDecodeResult.Decoded -> {
                fromCreated(ReferenceImage.create(decoded.width, decoded.height, decoded.packedRgba8888))
            }

            ReferenceImageDecodeResult.TooManyPixels -> {
                rejected(ReferenceImageSourceRejection.TooManyPixels)
            }

            ReferenceImageDecodeResult.Unsupported -> {
                rejected(ReferenceImageSourceRejection.Unsupported)
            }
        }

    private fun fromCreated(created: ReferenceImageResult): ReferenceImageOutcome =
        when (created) {
            is ReferenceImageResult.Created -> ReferenceImageOutcome.Picked(created.image)
            is ReferenceImageResult.Rejected -> rejected(ReferenceImageSourceRejection.Unsupported)
        }

    private fun fromReadFailure(failure: ProjectStorageFailure): ReferenceImageOutcome =
        if (failure == ProjectStorageFailure.ResourceLimitExceeded) {
            rejected(ReferenceImageSourceRejection.TooManyBytes)
        } else {
            ReferenceImageOutcome.Failed(failure)
        }

    private fun rejected(reason: ReferenceImageSourceRejection): ReferenceImageOutcome =
        ReferenceImageOutcome.Rejected(reason)

    public companion object {
        internal fun create(
            content: ProjectContentAccess,
            picker: ProjectPickerAccess,
            ioDispatcher: CoroutineDispatcher,
            decoder: ReferenceImageDecoder,
        ): ReferenceImagePort =
            AndroidReferenceImageAdapter(
                ProjectDocumentReader(content, ReferenceImageLimits.MAX_PROBE_BYTE_COUNT),
                picker,
                ioDispatcher,
                decoder,
            )
    }
}
