package io.github.hideyukimori.nenepixel.adapters.persistence

import android.util.AtomicFile
import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGenerationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspection
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRecordPort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRollbackOutcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

public class AndroidRecoveryRecordAdapter private constructor(
    private val reader: RecoveryRecordReader,
    private val writer: RecoveryRetirementWriter,
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

    private fun retireSerialized(expected: ExpectedRecoveryLineage): RecoveryRetirementOutcome =
        when (val actual = reader.inspect()) {
            is InternalInspection.Failed -> {
                RecoveryRetirementOutcome.Failed(
                    RecoveryRetirementFailure.CURRENT_RECORD_INSPECTION,
                    RecoveryRollbackOutcome.NOT_NEEDED,
                )
            }

            InternalInspection.Missing,
            is InternalInspection.Record,
            -> {
                retireLineage(expected, actual)
            }
        }

    private fun retireLineage(
        expected: ExpectedRecoveryLineage,
        actual: InternalInspection,
    ): RecoveryRetirementOutcome =
        if (matches(expected, actual)) {
            retireGeneration(generationValue(actual))
        } else {
            RecoveryRetirementOutcome.Stale
        }

    private fun retireGeneration(actualGeneration: Long): RecoveryRetirementOutcome =
        if (actualGeneration == Long.MAX_VALUE) {
            RecoveryRetirementOutcome.GenerationExhausted
        } else {
            publishRetired(createGeneration(actualGeneration + 1L))
        }

    private fun publishRetired(generation: RecoveryGeneration): RecoveryRetirementOutcome =
        when (val encoded = RecoveryRecordCodec.encodeRetired(generation)) {
            is RecoveryEncodeResult.Encoded -> {
                writer.publish(encoded.bytes, generation)
            }

            RecoveryEncodeResult.Rejected -> {
                RecoveryRetirementOutcome.Failed(
                    RecoveryRetirementFailure.RETIRED_ENCODING,
                    RecoveryRollbackOutcome.NOT_NEEDED,
                )
            }
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

    private fun matches(
        expected: ExpectedRecoveryLineage,
        actual: InternalInspection,
    ): Boolean =
        when (expected) {
            ExpectedRecoveryLineage.Missing -> actual is InternalInspection.Missing
            is ExpectedRecoveryLineage.Present -> generationValue(actual) == expected.generation.value
        }

    private fun generationValue(inspection: InternalInspection): Long =
        when (inspection) {
            InternalInspection.Missing -> 0L
            is InternalInspection.Record -> inspection.record.generation.value
            is InternalInspection.Failed -> error("Failed inspection has no recovery generation")
        }

    private fun createGeneration(value: Long): RecoveryGeneration =
        when (val result = RecoveryGeneration.create(value)) {
            is RecoveryGenerationResult.Created -> result.generation
            RecoveryGenerationResult.Rejected -> error("Validated next recovery generation was rejected")
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
            return AndroidRecoveryRecordAdapter(reader, RecoveryRetirementWriter(file, reader), ioDispatcher)
        }
    }
}

private object RecoveryRecordSerialization {
    val mutex: Mutex = Mutex()
}
