package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRollbackOutcome
import kotlinx.coroutines.CancellationException

internal class RecoveryRecordWriter(
    private val file: RecoveryAtomicFileAccess,
    reader: RecoveryRecordReader,
) {
    private val verifier = RecoveryPublicationVerifier(reader)

    fun publish(
        expectedBytes: ByteArray,
        generation: RecoveryGeneration,
        expectedRecord: (RecoveryRecord) -> Boolean,
    ): RecordWriteResult =
        when (val start = AtomicAccessResult.of { file.startWrite() }) {
            is AtomicAccessResult.Accepted -> {
                completePreFinish(start.value, expectedBytes)
                    ?: finish(start.value, expectedBytes, generation, expectedRecord)
            }

            AtomicAccessResult.Failed -> {
                RecordWriteResult.Failed(
                    RecoveryRetirementFailure.START_WRITE,
                    RecoveryRollbackOutcome.NOT_NEEDED,
                )
            }
        }

    private fun completePreFinish(
        session: RecoveryWriteSession,
        expectedBytes: ByteArray,
    ): RecordWriteResult? =
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
    ): RecordWriteResult =
        when (AtomicAccessResult.of { session.fail() }) {
            is AtomicAccessResult.Accepted -> {
                RecordWriteResult.Failed(primary, RecoveryRollbackOutcome.COMPLETED)
            }

            AtomicAccessResult.Failed -> {
                RecordWriteResult.Uncertain(primary, RecoveryRollbackOutcome.FAILED)
            }
        }

    private fun finish(
        session: RecoveryWriteSession,
        expectedBytes: ByteArray,
        generation: RecoveryGeneration,
        expectedRecord: (RecoveryRecord) -> Boolean,
    ): RecordWriteResult {
        val failure =
            try {
                operationFailure(RecoveryRetirementFailure.FINISH) { session.finish() }
            } catch (cancelled: CancellationException) {
                AtomicAccessResult.of { session.fail() }
                throw cancelled
            }
        return if (failure == null) {
            verifier.verifyPublished(expectedBytes, generation, expectedRecord)
        } else {
            RecordWriteResult.Uncertain(failure, RecoveryRollbackOutcome.NOT_NEEDED)
        }
    }

    private fun operationFailure(
        failure: RecoveryRetirementFailure,
        operation: () -> Unit,
    ): RecoveryRetirementFailure? =
        when (AtomicAccessResult.of(operation)) {
            is AtomicAccessResult.Accepted -> null
            AtomicAccessResult.Failed -> failure
        }
}

internal sealed interface RecordWriteResult {
    data class Written(
        val generation: RecoveryGeneration,
    ) : RecordWriteResult

    data class Failed(
        val failure: RecoveryRetirementFailure,
        val rollback: RecoveryRollbackOutcome,
    ) : RecordWriteResult

    data class Uncertain(
        val failure: RecoveryRetirementFailure,
        val rollback: RecoveryRollbackOutcome,
    ) : RecordWriteResult
}
