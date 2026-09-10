package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRecordPort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

private const val WARMUP_COUNT: Int = 5
private const val SAMPLE_COUNT: Int = 20
private const val SAMPLE_ANOMALY_NANOS: Long = 1_000_000_000L
private const val FIRST_GENERATION: Long = 1L
private const val NEXT_GENERATION: Long = 2L

internal fun main() {
    val fixture = RecoveryHostEvidenceFixture()
    fixture.verifyGoldenIdentity()
    fixture.verifyRetirementRoundTrip()
    reportMetadata()
    val observations = fixture.measureAll()
    fixture.verifyGoldenIdentity()
    fixture.verifyRetirementRoundTrip()
    reportSummaries(observations)
}

private fun <T> measure(
    group: String,
    operation: () -> T,
    verify: (T) -> Unit,
    prepare: () -> Unit = {},
): List<HostLatencyObservation> {
    repeat(WARMUP_COUNT) {
        prepare()
        verify(operation())
    }
    return List(SAMPLE_COUNT) { sampleIndex ->
        prepare()
        val start = System.nanoTime()
        val result = operation()
        val latencyNanos = System.nanoTime() - start
        println("$group,$sampleIndex,$latencyNanos")
        System.out.flush()
        verify(result)
        check(latencyNanos <= SAMPLE_ANOMALY_NANOS) { "$group sample $sampleIndex exceeded one second" }
        HostLatencyObservation(group, sampleIndex, latencyNanos)
    }
}

private fun reportMetadata() {
    println("schema,nene-pixel-p3-recovery-record-host-latency-v1")
    println("java_version,${System.getProperty("java.version")}")
    println("java_vm,${System.getProperty("java.vm.name")}")
    println("os,${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
    println("warmups,$WARMUP_COUNT")
    println("samples_per_group,$SAMPLE_COUNT")
    println("group,sample,latency_nanos")
    System.out.flush()
}

private fun reportSummaries(observations: List<HostLatencyObservation>) {
    observations.groupBy(HostLatencyObservation::group).forEach { (group, rows) ->
        val minimum = rows.minOf(HostLatencyObservation::latencyNanos)
        val maximum = rows.maxOf(HostLatencyObservation::latencyNanos)
        println("summary,$group,$minimum,$maximum")
    }
    System.out.flush()
}

private class RecoveryHostEvidenceFixture {
    private val document: DocumentState = PersistenceTestValues.maximumDocument()
    private val candidateGeneration: RecoveryGeneration = PersistenceTestValues.generation(FIRST_GENERATION)
    private val retiredGeneration: RecoveryGeneration = PersistenceTestValues.generation(NEXT_GENERATION)
    private val retiredBytes: ByteArray =
        RecoveryRecordEvidenceChecks.encoded(RecoveryRecordCodec.encodeRetired(candidateGeneration))
    private val candidateBytes: ByteArray =
        RecoveryRecordEvidenceChecks.encoded(RecoveryRecordCodec.encodeCandidate(candidateGeneration, document))
    private val record: InMemoryRecoveryRecordFile = InMemoryRecoveryRecordFile(candidateBytes)
    private val port: RecoveryRecordPort = AndroidRecoveryRecordAdapter.create(record, Dispatchers.Unconfined)

    fun measureAll(): List<HostLatencyObservation> = retiredGroups() + candidateGroups() + retirementGroup()

    fun verifyGoldenIdentity() {
        check(retiredBytes.size == RecoveryRecordLayout.RETIRED_BYTE_COUNT)
        check(candidateBytes.size == RecoveryRecordCodec.MAX_RECORD_BYTE_COUNT)
        val reencodedRetired =
            RecoveryRecordEvidenceChecks.encoded(RecoveryRecordCodec.encodeRetired(candidateGeneration))
        check(reencodedRetired.contentEquals(retiredBytes))
        val reencodedCandidate =
            RecoveryRecordEvidenceChecks.encoded(
                RecoveryRecordCodec.encodeCandidate(candidateGeneration, document),
            )
        check(reencodedCandidate.contentEquals(candidateBytes))
        check(
            RecoveryRecordEvidenceChecks.acceptedRecord(RecoveryRecordCodec.decode(retiredBytes)) ==
                RecoveryRecord.Retired(candidateGeneration),
        )
        check(
            RecoveryRecordEvidenceChecks.acceptedRecord(RecoveryRecordCodec.decode(candidateBytes)) ==
                RecoveryRecord.Candidate(candidateGeneration, document),
        )
    }

    fun verifyRetirementRoundTrip() {
        resetCandidate()
        val outcome = runBlocking { port.retire(ExpectedRecoveryLineage.Present(candidateGeneration)) }
        check(outcome == RecoveryRetirementOutcome.Retired(retiredGeneration))
        check(
            RecoveryRecordEvidenceChecks.acceptedRecord(RecoveryRecordCodec.decode(record.snapshot())) ==
                RecoveryRecord.Retired(retiredGeneration),
        )
        resetCandidate()
    }

    private fun retiredGroups(): List<HostLatencyObservation> =
        measure(
            "retired_encode",
            { RecoveryRecordCodec.encodeRetired(candidateGeneration) },
            { result -> RecoveryRecordEvidenceChecks.verifyEncoded(result, RecoveryRecordLayout.RETIRED_BYTE_COUNT) },
        ) +
            measure(
                "retired_decode",
                { RecoveryRecordCodec.decode(retiredBytes) },
                { result -> RecoveryRecordEvidenceChecks.verifyRetiredDecode(result, FIRST_GENERATION) },
            )

    private fun candidateGroups(): List<HostLatencyObservation> =
        measure(
            "candidate_encode",
            { RecoveryRecordCodec.encodeCandidate(candidateGeneration, document) },
            { result ->
                RecoveryRecordEvidenceChecks.verifyEncoded(result, RecoveryRecordCodec.MAX_RECORD_BYTE_COUNT)
            },
        ) +
            measure(
                "candidate_decode",
                { RecoveryRecordCodec.decode(candidateBytes) },
                { result -> RecoveryRecordEvidenceChecks.verifyCandidateDecode(result, FIRST_GENERATION) },
            )

    private fun retirementGroup(): List<HostLatencyObservation> =
        measure(
            "retirement_publish",
            { runBlocking { port.retire(ExpectedRecoveryLineage.Present(candidateGeneration)) } },
            ::verifyRetirement,
            ::resetCandidate,
        )

    private fun verifyRetirement(outcome: RecoveryRetirementOutcome) {
        check(outcome == RecoveryRetirementOutcome.Retired(retiredGeneration))
    }

    private fun resetCandidate() {
        record.reset(candidateBytes)
    }
}

private object RecoveryRecordEvidenceChecks {
    fun encoded(result: RecoveryEncodeResult): ByteArray =
        when (result) {
            is RecoveryEncodeResult.Encoded -> result.bytes
            RecoveryEncodeResult.Rejected -> error("Host evidence encode was rejected")
        }

    fun acceptedRecord(result: RecoveryDecodeResult): RecoveryRecord =
        when (result) {
            is RecoveryDecodeResult.Accepted -> result.record
            is RecoveryDecodeResult.Rejected -> error("Host evidence decode was rejected: ${result.rejection}")
        }

    fun verifyEncoded(
        result: RecoveryEncodeResult,
        expectedByteCount: Int,
    ) {
        check(encoded(result).size == expectedByteCount)
    }

    fun verifyRetiredDecode(
        result: RecoveryDecodeResult,
        expectedGeneration: Long,
    ) {
        val record = acceptedRecord(result)
        check(record is RecoveryRecord.Retired && record.generation.value == expectedGeneration)
    }

    fun verifyCandidateDecode(
        result: RecoveryDecodeResult,
        expectedGeneration: Long,
    ) {
        val record = acceptedRecord(result)
        check(record is RecoveryRecord.Candidate && record.generation.value == expectedGeneration)
    }
}

private class InMemoryRecoveryRecordFile(
    initialBytes: ByteArray,
) : RecoveryAtomicFileAccess {
    private var bytes: ByteArray = initialBytes.copyOf()

    fun reset(candidateBytes: ByteArray) {
        bytes = candidateBytes.copyOf()
    }

    fun snapshot(): ByteArray = bytes.copyOf()

    override fun openRead(): InputStream = ByteArrayInputStream(bytes.copyOf())

    override fun startWrite(): RecoveryWriteSession = InMemoryRecoveryWriteSession()

    private inner class InMemoryRecoveryWriteSession : RecoveryWriteSession {
        private val written = ByteArrayOutputStream()

        override val output: OutputStream
            get() = written

        override fun sync() = Unit

        override fun finish() {
            bytes = written.toByteArray()
        }

        override fun fail() = Unit
    }
}

private data class HostLatencyObservation(
    val group: String,
    val sampleIndex: Int,
    val latencyNanos: Long,
)
