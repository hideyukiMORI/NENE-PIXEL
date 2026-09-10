package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGenerationResult
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatBytes
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatRejection
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatResult
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatV1Codec

internal object RecoveryRecordDecoder {
    fun decode(bytes: ByteArray): RecoveryDecodeResult {
        val rejection = validatePrefix(bytes)
        return if (rejection == null) {
            decodeGeneration(bytes)
        } else {
            RecoveryDecodeResult.Rejected(rejection)
        }
    }

    private fun decodeGeneration(bytes: ByteArray): RecoveryDecodeResult =
        when (val generation = RecoveryRecordLayout.generation(bytes)) {
            is RecoveryGenerationResult.Created -> validateBody(bytes, generation.generation)
            RecoveryGenerationResult.Rejected -> RecoveryDecodeResult.Rejected(RecoveryRejection.CORRUPT)
        }

    private fun validateBody(
        bytes: ByteArray,
        generation: RecoveryGeneration,
    ): RecoveryDecodeResult {
        val state = RecoveryRecordLayout.state(bytes)
        val rejection = validateLength(state, bytes.size)
        return if (rejection == null) {
            validateChecksum(bytes, state, generation)
        } else {
            RecoveryDecodeResult.Rejected(rejection)
        }
    }

    private fun validateChecksum(
        bytes: ByteArray,
        state: Int,
        generation: RecoveryGeneration,
    ): RecoveryDecodeResult =
        if (RecoveryRecordLayout.hasValidChecksum(bytes)) {
            decodeState(bytes, state, generation)
        } else {
            RecoveryDecodeResult.Rejected(RecoveryRejection.CORRUPT)
        }

    private fun decodeState(
        bytes: ByteArray,
        state: Int,
        generation: RecoveryGeneration,
    ): RecoveryDecodeResult =
        when (state) {
            RecoveryRecordLayout.CANDIDATE_STATE -> decodeCandidate(bytes, generation)
            RecoveryRecordLayout.RETIRED_STATE -> RecoveryDecodeResult.Accepted(RecoveryRecord.Retired(generation))
            else -> RecoveryDecodeResult.Rejected(RecoveryRejection.CORRUPT)
        }

    private fun validatePrefix(bytes: ByteArray): RecoveryRejection? =
        when {
            bytes.size > RecoveryRecordLayout.MAX_RECORD_BYTE_COUNT -> RecoveryRejection.RESOURCE_LIMIT_EXCEEDED
            bytes.size < RecoveryRecordLayout.HEADER_BYTE_COUNT -> RecoveryRejection.CORRUPT
            !RecoveryRecordLayout.hasMagic(bytes) -> RecoveryRejection.CORRUPT
            RecoveryRecordLayout.version(bytes) != RecoveryRecordLayout.VERSION -> RecoveryRejection.UNSUPPORTED_VERSION
            else -> null
        }

    private fun validateLength(
        state: Int,
        byteCount: Int,
    ): RecoveryRejection? =
        when (state) {
            RecoveryRecordLayout.CANDIDATE_STATE -> {
                if (byteCount in CANDIDATE_BYTE_COUNTS) null else RecoveryRejection.CORRUPT
            }

            RecoveryRecordLayout.RETIRED_STATE -> {
                if (byteCount == RecoveryRecordLayout.RETIRED_BYTE_COUNT) null else RecoveryRejection.CORRUPT
            }

            else -> {
                RecoveryRejection.CORRUPT
            }
        }

    private fun decodeCandidate(
        bytes: ByteArray,
        generation: RecoveryGeneration,
    ): RecoveryDecodeResult =
        when (val carrier = ProjectFormatBytes.create(RecoveryRecordLayout.payload(bytes))) {
            is ProjectFormatResult.Rejected -> {
                RecoveryDecodeResult.Rejected(mapProjectRejection(carrier.rejection))
            }

            is ProjectFormatResult.Accepted -> {
                decodeCandidateDocument(carrier.value, generation)
            }
        }

    private fun decodeCandidateDocument(
        payload: ProjectFormatBytes,
        generation: RecoveryGeneration,
    ): RecoveryDecodeResult =
        when (val decoded = ProjectFormatV1Codec.decode(payload)) {
            is ProjectFormatResult.Accepted -> {
                RecoveryDecodeResult.Accepted(RecoveryRecord.Candidate(generation, decoded.value))
            }

            is ProjectFormatResult.Rejected -> {
                RecoveryDecodeResult.Rejected(mapProjectRejection(decoded.rejection))
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
            -> RecoveryRejection.CORRUPT
        }

    private val CANDIDATE_BYTE_COUNTS: IntRange =
        RecoveryRecordLayout.MIN_CANDIDATE_BYTE_COUNT..RecoveryRecordLayout.MAX_RECORD_BYTE_COUNT
}
