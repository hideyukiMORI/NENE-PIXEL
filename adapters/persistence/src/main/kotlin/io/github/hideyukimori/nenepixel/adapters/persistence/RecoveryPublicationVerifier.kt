package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRollbackOutcome

internal class RecoveryPublicationVerifier(
    private val reader: RecoveryRecordReader,
) {
    fun verifyPublished(
        expectedBytes: ByteArray,
        generation: RecoveryGeneration,
        expectedRecord: (RecoveryRecord) -> Boolean,
    ): RecordWriteResult =
        when (val read = reader.readRaw()) {
            is RawRecordRead.Bytes -> {
                compareReadBack(read.value, expectedBytes, generation, expectedRecord)
            }

            RawRecordRead.Missing,
            RawRecordRead.ResourceLimitExceeded,
            RawRecordRead.ReadFailed,
            RawRecordRead.CloseFailed,
            -> {
                uncertain(RecoveryRetirementFailure.READ_BACK)
            }
        }

    private fun compareReadBack(
        actualBytes: ByteArray,
        expectedBytes: ByteArray,
        generation: RecoveryGeneration,
        expectedRecord: (RecoveryRecord) -> Boolean,
    ): RecordWriteResult =
        if (actualBytes.contentEquals(expectedBytes)) {
            verifyDecoded(actualBytes, generation, expectedRecord)
        } else {
            uncertain(RecoveryRetirementFailure.READ_BACK_MISMATCH)
        }

    private fun verifyDecoded(
        actualBytes: ByteArray,
        generation: RecoveryGeneration,
        expectedRecord: (RecoveryRecord) -> Boolean,
    ): RecordWriteResult =
        when (val decoded = RecoveryRecordCodec.decode(actualBytes)) {
            is RecoveryDecodeResult.Accepted -> verifyRecord(decoded.record, generation, expectedRecord)
            is RecoveryDecodeResult.Rejected -> uncertain(RecoveryRetirementFailure.READ_BACK_MISMATCH)
        }

    private fun verifyRecord(
        record: RecoveryRecord,
        generation: RecoveryGeneration,
        expectedRecord: (RecoveryRecord) -> Boolean,
    ): RecordWriteResult =
        if (expectedRecord(record)) {
            RecordWriteResult.Written(generation)
        } else {
            uncertain(RecoveryRetirementFailure.READ_BACK_MISMATCH)
        }

    private fun uncertain(failure: RecoveryRetirementFailure): RecordWriteResult =
        RecordWriteResult.Uncertain(failure, RecoveryRollbackOutcome.NOT_NEEDED)
}
