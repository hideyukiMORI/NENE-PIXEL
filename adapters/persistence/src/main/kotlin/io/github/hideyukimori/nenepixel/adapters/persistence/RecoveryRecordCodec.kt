package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatV1Codec

internal object RecoveryRecordCodec {
    const val MAX_RECORD_BYTE_COUNT: Int = RecoveryRecordLayout.MAX_RECORD_BYTE_COUNT
    const val MAX_PROBE_BYTE_COUNT: Int = RecoveryRecordLayout.MAX_PROBE_BYTE_COUNT

    fun decode(bytes: ByteArray): RecoveryDecodeResult = RecoveryRecordDecoder.decode(bytes)

    fun encodeRetired(generation: RecoveryGeneration): RecoveryEncodeResult {
        val bytes = RecoveryRecordLayout.encode(RecoveryRecordLayout.RETIRED_STATE, generation, EMPTY_PAYLOAD)
        return verified(bytes) { record -> record == RecoveryRecord.Retired(generation) }
    }

    fun encodeCandidate(
        generation: RecoveryGeneration,
        document: DocumentState,
    ): RecoveryEncodeResult {
        val payload = ProjectFormatV1Codec.encode(document).copyBytes()
        val bytes = RecoveryRecordLayout.encode(RecoveryRecordLayout.CANDIDATE_STATE, generation, payload)
        return verified(bytes) { record ->
            record is RecoveryRecord.Candidate && record.generation == generation && record.document == document
        }
    }

    private fun verified(
        bytes: ByteArray,
        matches: (RecoveryRecord) -> Boolean,
    ): RecoveryEncodeResult =
        when (val decoded = decode(bytes)) {
            is RecoveryDecodeResult.Accepted -> {
                if (matches(decoded.record)) RecoveryEncodeResult.Encoded(bytes) else RecoveryEncodeResult.Rejected
            }

            is RecoveryDecodeResult.Rejected -> {
                RecoveryEncodeResult.Rejected
            }
        }

    private val EMPTY_PAYLOAD: ByteArray = ByteArray(0)
}

internal sealed interface RecoveryRecord {
    val generation: RecoveryGeneration

    data class Retired(
        override val generation: RecoveryGeneration,
    ) : RecoveryRecord

    data class Candidate(
        override val generation: RecoveryGeneration,
        val document: DocumentState,
    ) : RecoveryRecord
}

internal sealed interface RecoveryDecodeResult {
    data class Accepted(
        val record: RecoveryRecord,
    ) : RecoveryDecodeResult

    data class Rejected(
        val rejection: RecoveryRejection,
    ) : RecoveryDecodeResult
}

internal enum class RecoveryRejection {
    RESOURCE_LIMIT_EXCEEDED,
    UNSUPPORTED_VERSION,
    CORRUPT,
}

internal sealed interface RecoveryEncodeResult {
    data class Encoded(
        val bytes: ByteArray,
    ) : RecoveryEncodeResult

    data object Rejected : RecoveryEncodeResult
}
