package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRollbackOutcome
import kotlinx.coroutines.CancellationException

internal class RecoveryRetirementWriter(
    private val file: RecoveryAtomicFileAccess,
    private val reader: RecoveryRecordReader,
) {
    fun publish(
        expectedBytes: ByteArray,
        generation: RecoveryGeneration,
    ): RecoveryRetirementOutcome =
        when (val start = AtomicAccessResult.of { file.startWrite() }) {
            is AtomicAccessResult.Accepted -> {
                completePreFinish(start.value, expectedBytes) ?: finish(start.value, expectedBytes, generation)
            }

            AtomicAccessResult.Failed -> {
                RecoveryRetirementOutcome.Failed(
                    RecoveryRetirementFailure.START_WRITE,
                    RecoveryRollbackOutcome.NOT_NEEDED,
                )
            }
        }

    private fun completePreFinish(
        session: RecoveryWriteSession,
        expectedBytes: ByteArray,
    ): RecoveryRetirementOutcome? =
        try {
            val written = operationFailure(RecoveryRetirementFailure.WRITE) { session.output.write(expectedBytes) }
            val failure = written ?: operationFailure(RecoveryRetirementFailure.SYNC) { session.sync() }
            if (failure == null) null else rollback(session, failure)
        } catch (cancelled: CancellationException) {
            AtomicAccessResult.of { session.fail() }
            throw cancelled
        }

    private fun rollback(
        session: RecoveryWriteSession,
        primary: RecoveryRetirementFailure,
    ): RecoveryRetirementOutcome =
        when (AtomicAccessResult.of { session.fail() }) {
            is AtomicAccessResult.Accepted -> {
                RecoveryRetirementOutcome.Failed(primary, RecoveryRollbackOutcome.COMPLETED)
            }

            AtomicAccessResult.Failed -> {
                RecoveryRetirementOutcome.Uncertain(primary, RecoveryRollbackOutcome.FAILED)
            }
        }

    private fun finish(
        session: RecoveryWriteSession,
        expectedBytes: ByteArray,
        generation: RecoveryGeneration,
    ): RecoveryRetirementOutcome {
        val failure =
            try {
                operationFailure(RecoveryRetirementFailure.FINISH) { session.finish() }
            } catch (cancelled: CancellationException) {
                AtomicAccessResult.of { session.fail() }
                throw cancelled
            }
        return if (failure == null) {
            verifyPublished(expectedBytes, generation)
        } else {
            RecoveryRetirementOutcome.Uncertain(failure, RecoveryRollbackOutcome.NOT_NEEDED)
        }
    }

    private fun verifyPublished(
        expectedBytes: ByteArray,
        generation: RecoveryGeneration,
    ): RecoveryRetirementOutcome =
        when (val read = reader.readRaw()) {
            is RawRecordRead.Bytes -> {
                compareReadBack(read.value, expectedBytes, generation)
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
    ): RecoveryRetirementOutcome =
        if (actualBytes.contentEquals(expectedBytes)) {
            verifyDecoded(actualBytes, generation)
        } else {
            uncertain(RecoveryRetirementFailure.READ_BACK_MISMATCH)
        }

    private fun verifyDecoded(
        actualBytes: ByteArray,
        generation: RecoveryGeneration,
    ): RecoveryRetirementOutcome =
        when (val decoded = RecoveryRecordCodec.decode(actualBytes)) {
            is RecoveryDecodeResult.Accepted -> verifyRetiredRecord(decoded.record, generation)
            is RecoveryDecodeResult.Rejected -> uncertain(RecoveryRetirementFailure.READ_BACK_MISMATCH)
        }

    private fun verifyRetiredRecord(
        record: RecoveryRecord,
        generation: RecoveryGeneration,
    ): RecoveryRetirementOutcome =
        if (record is RecoveryRecord.Retired && record.generation == generation) {
            RecoveryRetirementOutcome.Retired(generation)
        } else {
            uncertain(RecoveryRetirementFailure.READ_BACK_MISMATCH)
        }

    private fun operationFailure(
        failure: RecoveryRetirementFailure,
        operation: () -> Unit,
    ): RecoveryRetirementFailure? =
        when (AtomicAccessResult.of(operation)) {
            is AtomicAccessResult.Accepted -> null
            AtomicAccessResult.Failed -> failure
        }

    private fun uncertain(failure: RecoveryRetirementFailure): RecoveryRetirementOutcome =
        RecoveryRetirementOutcome.Uncertain(failure, RecoveryRollbackOutcome.NOT_NEEDED)
}
