package io.github.hideyukimori.nenepixel.adapters.persistence

import android.util.AtomicFile
import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspection
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryPublicationOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRecordPort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRollbackOutcome
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

public class AndroidRecoveryRecordAdapter private constructor(
    private val reader: RecoveryRecordReader,
    private val writer: RecoveryRecordWriter,
    private val ioDispatcher: CoroutineDispatcher,
) : RecoveryRecordPort {
    override suspend fun inspect(): RecoveryInspection =
        withContext(ioDispatcher) {
            RecoveryRecordSerialization.mutex.withLock {
                mapInspection(reader.inspect())
            }
        }

    override suspend fun retire(expected: ExpectedRecoveryLineage): RecoveryRetirementOutcome =
        withContext(ioDispatcher) {
            RecoveryRecordSerialization.mutex.withLock {
                retireSerialized(expected)
            }
        }

    override suspend fun publishCandidate(
        expected: ExpectedRecoveryLineage,
        document: DocumentState,
    ): RecoveryPublicationOutcome =
        withContext(ioDispatcher) {
            RecoveryRecordSerialization.mutex.withLock {
                publishCandidateSerialized(expected, document)
            }
        }

    private fun retireSerialized(expected: ExpectedRecoveryLineage): RecoveryRetirementOutcome =
        when (val step = RecoveryLineageMatcher.nextGeneration(expected, reader.inspect())) {
            is RecoveryGenerationStep.Next -> {
                publishRetired(step.generation)
            }

            RecoveryGenerationStep.InspectionFailed -> {
                RecoveryRetirementOutcome.Failed(
                    RecoveryRetirementFailure.CURRENT_RECORD_INSPECTION,
                    RecoveryRollbackOutcome.NOT_NEEDED,
                )
            }

            RecoveryGenerationStep.Stale -> {
                RecoveryRetirementOutcome.Stale
            }

            RecoveryGenerationStep.Exhausted -> {
                RecoveryRetirementOutcome.GenerationExhausted
            }
        }

    private fun publishCandidateSerialized(
        expected: ExpectedRecoveryLineage,
        document: DocumentState,
    ): RecoveryPublicationOutcome =
        when (val step = RecoveryLineageMatcher.nextGeneration(expected, reader.inspect())) {
            is RecoveryGenerationStep.Next -> {
                publishCandidateRecord(step.generation, document)
            }

            RecoveryGenerationStep.InspectionFailed -> {
                RecoveryPublicationOutcome.Failed(
                    RecoveryRetirementFailure.CURRENT_RECORD_INSPECTION,
                    RecoveryRollbackOutcome.NOT_NEEDED,
                )
            }

            RecoveryGenerationStep.Stale -> {
                RecoveryPublicationOutcome.Stale
            }

            RecoveryGenerationStep.Exhausted -> {
                RecoveryPublicationOutcome.GenerationExhausted
            }
        }

    private fun publishRetired(generation: RecoveryGeneration): RecoveryRetirementOutcome =
        when (val encoded = RecoveryRecordCodec.encodeRetired(generation)) {
            is RecoveryEncodeResult.Encoded -> {
                retirementOutcome(
                    writer.publish(encoded.bytes, generation) { record ->
                        record == RecoveryRecord.Retired(generation)
                    },
                )
            }

            RecoveryEncodeResult.Rejected -> {
                RecoveryRetirementOutcome.Failed(
                    RecoveryRetirementFailure.RETIRED_ENCODING,
                    RecoveryRollbackOutcome.NOT_NEEDED,
                )
            }
        }

    // ADR 0014: the candidate is encoded and validated before the write session is opened.
    private fun publishCandidateRecord(
        generation: RecoveryGeneration,
        document: DocumentState,
    ): RecoveryPublicationOutcome =
        when (val encoded = RecoveryRecordCodec.encodeCandidate(generation, document)) {
            is RecoveryEncodeResult.Encoded -> {
                publicationOutcome(
                    writer.publish(encoded.bytes, generation) { record ->
                        record == RecoveryRecord.Candidate(generation, document)
                    },
                )
            }

            RecoveryEncodeResult.Rejected -> {
                RecoveryPublicationOutcome.Failed(
                    RecoveryRetirementFailure.CANDIDATE_ENCODING,
                    RecoveryRollbackOutcome.NOT_NEEDED,
                )
            }
        }

    private fun retirementOutcome(result: RecordWriteResult): RecoveryRetirementOutcome =
        when (result) {
            is RecordWriteResult.Written -> RecoveryRetirementOutcome.Retired(result.generation)
            is RecordWriteResult.Failed -> RecoveryRetirementOutcome.Failed(result.failure, result.rollback)
            is RecordWriteResult.Uncertain -> RecoveryRetirementOutcome.Uncertain(result.failure, result.rollback)
        }

    private fun publicationOutcome(result: RecordWriteResult): RecoveryPublicationOutcome =
        when (result) {
            is RecordWriteResult.Written -> RecoveryPublicationOutcome.Published(result.generation)
            is RecordWriteResult.Failed -> RecoveryPublicationOutcome.Failed(result.failure, result.rollback)
            is RecordWriteResult.Uncertain -> RecoveryPublicationOutcome.Uncertain(result.failure, result.rollback)
        }

    private fun mapInspection(inspection: InternalInspection): RecoveryInspection =
        when (inspection) {
            InternalInspection.Missing -> {
                RecoveryInspection.Missing
            }

            is InternalInspection.Failed -> {
                RecoveryInspection.Failed(inspection.failure)
            }

            is InternalInspection.Record -> {
                when (val record = inspection.record) {
                    is RecoveryRecord.Retired -> RecoveryInspection.Retired(record.generation)
                    is RecoveryRecord.Candidate -> RecoveryInspection.Candidate(record.generation, record.document)
                }
            }
        }

    public companion object {
        public fun create(
            atomicFile: AtomicFile,
            ioDispatcher: CoroutineDispatcher,
        ): RecoveryRecordPort =
            create(
                AndroidRecoveryAtomicFileAccess(atomicFile),
                ioDispatcher,
            )

        internal fun create(
            file: RecoveryAtomicFileAccess,
            ioDispatcher: CoroutineDispatcher,
        ): RecoveryRecordPort {
            val reader = RecoveryRecordReader(file)
            return AndroidRecoveryRecordAdapter(reader, RecoveryRecordWriter(file, reader), ioDispatcher)
        }
    }
}

private object RecoveryRecordSerialization {
    val mutex: Mutex = Mutex()
}
