package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGenerationResult
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatBytes
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatCodec
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatRejection
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatResult

internal object RecoveryRecordDecoder {
    fun decode(bytes: ByteArray): RecoveryDecodeResult {
        val rejection = validatePrefix(bytes)
        return if (rejection == null) {
            decodeGeneration(bytes, RecoveryRecordLayout.version(bytes))
        } else {
            RecoveryDecodeResult.Rejected(rejection)
        }
    }

    private fun decodeGeneration(
        bytes: ByteArray,
        version: Int,
    ): RecoveryDecodeResult =
        when (val generation = RecoveryRecordLayout.generation(bytes)) {
            is RecoveryGenerationResult.Created -> validateBody(bytes, version, generation.generation)
            RecoveryGenerationResult.Rejected -> RecoveryDecodeResult.Rejected(RecoveryRejection.CORRUPT)
        }

    private fun validateBody(
        bytes: ByteArray,
        version: Int,
        generation: RecoveryGeneration,
    ): RecoveryDecodeResult {
        val state = RecoveryRecordLayout.state(bytes)
        val rejection = RecoveryRecordLengthPolicy.validate(version, state, bytes.size)
        return if (rejection == null) {
            validateChecksum(bytes, version, state, generation)
        } else {
            RecoveryDecodeResult.Rejected(rejection)
        }
    }

    private fun validateChecksum(
        bytes: ByteArray,
        version: Int,
        state: Int,
        generation: RecoveryGeneration,
    ): RecoveryDecodeResult =
        if (RecoveryRecordLayout.hasValidChecksum(bytes)) {
            decodeState(bytes, version, state, generation)
        } else {
            RecoveryDecodeResult.Rejected(RecoveryRejection.CORRUPT)
        }

    private fun decodeState(
        bytes: ByteArray,
        version: Int,
        state: Int,
        generation: RecoveryGeneration,
    ): RecoveryDecodeResult =
        when (state) {
            RecoveryRecordLayout.CANDIDATE_STATE -> decodeCandidate(bytes, version, generation)
            RecoveryRecordLayout.RETIRED_STATE -> RecoveryDecodeResult.Accepted(RecoveryRecord.Retired(generation))
            else -> RecoveryDecodeResult.Rejected(RecoveryRejection.CORRUPT)
        }

    private fun validatePrefix(bytes: ByteArray): RecoveryRejection? {
        val version =
            if (bytes.size >= RecoveryRecordLayout.HEADER_BYTE_COUNT) {
                RecoveryRecordLayout.version(bytes)
            } else {
                RecoveryRecordLayout.V1_VERSION
            }
        return when {
            bytes.size > RecoveryRecordLayout.MAX_RECORD_BYTE_COUNT -> {
                RecoveryRejection.RESOURCE_LIMIT_EXCEEDED
            }

            bytes.size < RecoveryRecordLayout.HEADER_BYTE_COUNT -> {
                RecoveryRejection.CORRUPT
            }

            !RecoveryRecordLayout.hasMagic(bytes) -> {
                RecoveryRejection.CORRUPT
            }

            version !in RecoveryRecordLayout.V1_VERSION..RecoveryRecordLayout.V4_VERSION -> {
                RecoveryRejection.UNSUPPORTED_VERSION
            }

            else -> {
                null
            }
        }
    }

    private fun decodeCandidate(
        bytes: ByteArray,
        version: Int,
        generation: RecoveryGeneration,
    ): RecoveryDecodeResult {
        // The envelope's length and CRC have already admitted this fixed nested prefix.
        val offset = RecoveryRecordLayout.PAYLOAD_OFFSET + NESTED_VERSION_OFFSET
        val nestedVersion =
            (RecoveryRecordBigEndian.unsignedByte(bytes[offset]) shl 8) or
                RecoveryRecordBigEndian.unsignedByte(bytes[offset + 1])
        return if (nestedVersion != version) {
            RecoveryDecodeResult.Rejected(RecoveryRejection.CORRUPT)
        } else {
            decodeMatchingCandidate(bytes, generation)
        }
    }

    private fun decodeMatchingCandidate(
        bytes: ByteArray,
        generation: RecoveryGeneration,
    ): RecoveryDecodeResult =
        when (val carrier = ProjectFormatBytes.create(RecoveryRecordLayout.payload(bytes))) {
            is ProjectFormatResult.Rejected -> {
                RecoveryDecodeResult.Rejected(mapProjectRejection(carrier.rejection))
            }

            is ProjectFormatResult.Accepted -> {
                when (val decoded = ProjectFormatCodec.decode(carrier.value)) {
                    is ProjectFormatResult.Accepted -> {
                        RecoveryDecodeResult.Accepted(RecoveryRecord.Candidate(generation, decoded.value))
                    }

                    is ProjectFormatResult.Rejected -> {
                        RecoveryDecodeResult.Rejected(mapProjectRejection(decoded.rejection))
                    }
                }
            }
        }

    private fun mapProjectRejection(rejection: ProjectFormatRejection): RecoveryRejection =
        when (rejection) {
            is ProjectFormatRejection.ResourceLimitExceeded -> RecoveryRejection.RESOURCE_LIMIT_EXCEEDED

            is ProjectFormatRejection.UnsupportedVersion -> RecoveryRejection.UNSUPPORTED_VERSION

            is ProjectFormatRejection.Truncated,
            ProjectFormatRejection.InvalidMagic,
            ProjectFormatRejection.InvalidCanvas,
            ProjectFormatRejection.InvalidRevision,
            is ProjectFormatRejection.TrailingData,
            is ProjectFormatRejection.ChecksumMismatch,
            is ProjectFormatRejection.InvalidPaletteEntryCount,
            is ProjectFormatRejection.DefaultIndexOutsidePalette,
            is ProjectFormatRejection.PixelIndexOutsidePalette,
            is ProjectFormatRejection.InvalidFrameCount,
            is ProjectFormatRejection.InvalidFrameId,
            is ProjectFormatRejection.DuplicateFrameId,
            is ProjectFormatRejection.InvalidFrameDuration,
            is ProjectFormatRejection.InvalidTotalLayerCount,
            is ProjectFormatRejection.InvalidLayerCount,
            is ProjectFormatRejection.InvalidLayerId,
            is ProjectFormatRejection.DuplicateLayerId,
            is ProjectFormatRejection.UnknownLayerFlags,
            is ProjectFormatRejection.InvalidLayerName,
            is ProjectFormatRejection.InvalidCoverage,
            -> RecoveryRejection.CORRUPT
        }

    private const val NESTED_VERSION_OFFSET: Int = 8
}
