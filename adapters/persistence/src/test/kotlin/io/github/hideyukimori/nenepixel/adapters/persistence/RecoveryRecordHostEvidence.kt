package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryPublicationOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRecordPort
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

private const val WARMUP_COUNT: Int = 5
private const val SAMPLE_COUNT: Int = 20
private const val SAMPLE_ANOMALY_NANOS: Long = 1_000_000_000L
private const val CANDIDATE_ROLE: String = "candidate"
private const val FIRST_GENERATION: Long = 1L
private const val NEXT_GENERATION: Long = 2L

internal fun main(arguments: Array<String>) {
    require(arguments.contentEquals(arrayOf(CANDIDATE_ROLE))) { "Candidate host evidence requires sole role argument" }
    val fixture = RecoveryHostEvidenceFixture()
    reportMetadata()
    val observations = fixture.groups().flatMap { group -> group.measure() }
    reportSummaries(observations)
}

internal fun verifyRecoveryHostEvidenceFixtures() {
    RecoveryHostEvidenceFixture().groups().forEach { group -> group.verifyOnly() }
}

private class RecoveryHostEvidenceFixture {
    private val document: DocumentState = PersistenceTestValues.maximumDocument()
    private val legacy: LegacyRgbaSource = PersistenceTestValues.maximumLegacySource()
    private val first: RecoveryGeneration = PersistenceTestValues.generation(FIRST_GENERATION)
    private val next: RecoveryGeneration = PersistenceTestValues.generation(NEXT_GENERATION)
    private val retiredBytes = encoded(RecoveryRecordCodec.encodeRetired(first))
    private val legacyBytes =
        RecoveryRecordLayout.encode(
            RecoveryRecordLayout.V1_VERSION,
            RecoveryRecordLayout.CANDIDATE_STATE,
            first,
            ProjectFormatCodec.encodeLegacySource(legacy).copyBytes(),
        )
    private val candidateBytes = encoded(RecoveryRecordCodec.encodeCandidate(first, document))
    private val record = InMemoryRecoveryRecordFile(candidateBytes)
    private val port: RecoveryRecordPort = AndroidRecoveryRecordAdapter.create(record, Dispatchers.Unconfined)

    fun groups(): List<RecoveryHostEvidenceGroup<*>> =
        listOf(
            retiredEncodeGroup(),
            retiredDecodeGroup(),
            legacyDecodeGroup(),
            candidateEncodeGroup(),
            candidateDecodeGroup(),
            candidatePublicationGroup(),
        )

    private fun retiredEncodeGroup(): RecoveryHostEvidenceGroup<RecoveryEncodeResult> =
        RecoveryHostEvidenceGroup(
            "v2_retired_encode",
            { RecoveryRecordCodec.encodeRetired(first) },
            RecoveryHostEvidenceChecks(
                { result ->
                    verifyEncodedFacts(
                        result,
                        RecoveryRecordLayout.RETIRED_BYTE_COUNT,
                        RecoveryRecordLayout.RETIRED_STATE,
                    )
                },
                { verifyRetiredBoundary() },
            ),
        )

    private fun retiredDecodeGroup(): RecoveryHostEvidenceGroup<RecoveryDecodeResult> =
        RecoveryHostEvidenceGroup(
            "v2_retired_decode",
            { RecoveryRecordCodec.decode(retiredBytes) },
            RecoveryHostEvidenceChecks(
                { result -> verifyRetiredFacts(result, first) },
                { verifyRetiredBoundary() },
            ),
        )

    private fun legacyDecodeGroup(): RecoveryHostEvidenceGroup<RecoveryDecodeResult> =
        RecoveryHostEvidenceGroup(
            "v1_max_candidate_decode_legacy",
            { RecoveryRecordCodec.decode(legacyBytes) },
            RecoveryHostEvidenceChecks(
                { result -> verifyLegacyFacts(result, first, legacy) },
                { verifyLegacyBoundary() },
            ),
        )

    private fun candidateEncodeGroup(): RecoveryHostEvidenceGroup<RecoveryEncodeResult> =
        RecoveryHostEvidenceGroup(
            "v2_max_candidate_encode",
            { RecoveryRecordCodec.encodeCandidate(first, document) },
            RecoveryHostEvidenceChecks(
                { result ->
                    verifyEncodedFacts(
                        result,
                        RecoveryRecordLayout.V2_MAX_CANDIDATE_BYTE_COUNT,
                        RecoveryRecordLayout.CANDIDATE_STATE,
                    )
                },
                { verifyCandidateBoundary() },
            ),
        )

    private fun candidateDecodeGroup(): RecoveryHostEvidenceGroup<RecoveryDecodeResult> =
        RecoveryHostEvidenceGroup(
            "v2_max_candidate_decode_current",
            { RecoveryRecordCodec.decode(candidateBytes) },
            RecoveryHostEvidenceChecks(
                { result -> verifyCurrentFacts(result, first, document) },
                { verifyCandidateBoundary() },
            ),
        )

    private fun candidatePublicationGroup(): RecoveryHostEvidenceGroup<RecoveryPublicationOutcome> =
        RecoveryHostEvidenceGroup(
            "v2_max_candidate_publish",
            { runBlocking { port.publishCandidate(ExpectedRecoveryLineage.Present(first), document) } },
            RecoveryHostEvidenceChecks(
                { outcome -> check(outcome == RecoveryPublicationOutcome.Published(next)) },
                { verifyPublicationBoundary() },
                { record.reset(candidateBytes) },
            ),
        )

    private fun verifyRetiredBoundary() {
        check(encoded(RecoveryRecordCodec.encodeRetired(first)).contentEquals(retiredBytes))
        check(accepted(RecoveryRecordCodec.decode(retiredBytes)) == RecoveryRecord.Retired(first))
        check(RecoveryRecordLayout.hasValidChecksum(retiredBytes))
    }

    private fun verifyLegacyBoundary() {
        check(legacyBytes.size == RecoveryRecordLayout.MAX_RECORD_BYTE_COUNT)
        check(legacy.copyPackedRgba8888().toSet().size == 65_536)
        check(
            accepted(RecoveryRecordCodec.decode(legacyBytes)) ==
                RecoveryRecord.Candidate(first, DocumentImportSource.Legacy(legacy)),
        )
        check(RecoveryRecordLayout.hasValidChecksum(legacyBytes))
    }

    private fun verifyCandidateBoundary() {
        check(candidateBytes.size == RecoveryRecordLayout.V2_MAX_CANDIDATE_BYTE_COUNT)
        check(encoded(RecoveryRecordCodec.encodeCandidate(first, document)).contentEquals(candidateBytes))
        check(
            accepted(RecoveryRecordCodec.decode(candidateBytes)) ==
                RecoveryRecord.Candidate(first, DocumentImportSource.Current(document)),
        )
        check(RecoveryRecordLayout.hasValidChecksum(candidateBytes))
    }

    private fun verifyPublicationBoundary() {
        record.reset(candidateBytes)
        check(
            runBlocking { port.publishCandidate(ExpectedRecoveryLineage.Present(first), document) } ==
                RecoveryPublicationOutcome.Published(next),
        )
        check(
            accepted(RecoveryRecordCodec.decode(record.snapshot())) ==
                RecoveryRecord.Candidate(next, DocumentImportSource.Current(document)),
        )
        check(RecoveryRecordLayout.hasValidChecksum(record.snapshot()))
        record.reset(candidateBytes)
    }
}

private class RecoveryHostEvidenceGroup<T>(
    private val name: String,
    private val operation: () -> T,
    private val checks: RecoveryHostEvidenceChecks<T>,
) {
    fun verifyOnly() {
        checks.verifyBoundary()
    }

    fun measure(): List<RecoveryHostObservation> {
        checks.verifyBoundary()
        repeat(WARMUP_COUNT) {
            checks.prepare()
            checks.verifyFacts(operation())
        }
        val observations = List(SAMPLE_COUNT, ::measureSample)
        checks.verifyBoundary()
        return observations
    }

    private fun measureSample(index: Int): RecoveryHostObservation {
        checks.prepare()
        val start = System.nanoTime()
        val result = operation()
        val elapsed = System.nanoTime() - start
        val observation = RecoveryHostObservation(name, index, elapsed)
        println(RecoveryHostEvidenceReport.sampleRow(observation))
        System.out.flush()
        checks.verifyFacts(result)
        check(elapsed <= SAMPLE_ANOMALY_NANOS) { "$name sample $index exceeded one second" }
        return observation
    }
}

private class RecoveryHostEvidenceChecks<T>(
    val verifyFacts: (T) -> Unit,
    val verifyBoundary: () -> Unit,
    val prepare: () -> Unit = {},
)

internal object RecoveryHostEvidenceReport {
    const val SCHEMA: String = "nene-pixel-p4-recovery-record-host-v1"
    val GROUPS: List<String> =
        listOf(
            "v2_retired_encode",
            "v2_retired_decode",
            "v1_max_candidate_decode_legacy",
            "v2_max_candidate_encode",
            "v2_max_candidate_decode_current",
            "v2_max_candidate_publish",
        )

    fun sampleRow(observation: RecoveryHostObservation): String =
        "${observation.group},${observation.sampleIndex},${observation.latencyNanos}"

    fun parseSampleRow(row: String): RecoveryHostObservation {
        val fields = row.split(',')
        require(fields.size == 3 && fields[0] in GROUPS)
        val sample = fields[1].toInt()
        val latency = fields[2].toLong()
        require(sample in 0 until SAMPLE_COUNT && latency >= 0L)
        return RecoveryHostObservation(fields[0], sample, latency)
    }
}

internal data class RecoveryHostObservation(
    val group: String,
    val sampleIndex: Int,
    val latencyNanos: Long,
)

private fun verifyEncodedFacts(
    result: RecoveryEncodeResult,
    byteCount: Int,
    state: Int,
) {
    val bytes = encoded(result)
    check(bytes.size == byteCount)
    check(RecoveryRecordLayout.version(bytes) == RecoveryRecordLayout.V2_VERSION)
    check(RecoveryRecordLayout.state(bytes) == state)
}

private fun verifyRetiredFacts(
    result: RecoveryDecodeResult,
    generation: RecoveryGeneration,
) {
    val record = accepted(result)
    check(record is RecoveryRecord.Retired && record.generation == generation)
}

private fun verifyLegacyFacts(
    result: RecoveryDecodeResult,
    generation: RecoveryGeneration,
    expected: LegacyRgbaSource,
) {
    val record = accepted(result)
    val source = (record as RecoveryRecord.Candidate).source as DocumentImportSource.Legacy
    check(record.generation == generation)
    check(source.source.id == expected.id && source.source.revision == expected.revision)
    check(source.source.size == expected.size)
}

private fun verifyCurrentFacts(
    result: RecoveryDecodeResult,
    generation: RecoveryGeneration,
    expected: DocumentState,
) {
    val record = accepted(result)
    val document = ((record as RecoveryRecord.Candidate).source as DocumentImportSource.Current).document
    check(record.generation == generation)
    check(document.id == expected.id && document.revision == expected.revision && document.size == expected.size)
    check(document.definition.palette.entryCount == expected.definition.palette.entryCount)
    check(document.definition.defaultIndex == expected.definition.defaultIndex)
}

private fun encoded(result: RecoveryEncodeResult): ByteArray =
    when (result) {
        is RecoveryEncodeResult.Encoded -> result.bytes
        RecoveryEncodeResult.Rejected -> error("Host evidence encode was rejected")
    }

private fun accepted(result: RecoveryDecodeResult): RecoveryRecord =
    when (result) {
        is RecoveryDecodeResult.Accepted -> result.record
        is RecoveryDecodeResult.Rejected -> error("Host evidence decode was rejected: ${result.rejection}")
    }

private class InMemoryRecoveryRecordFile(
    initialBytes: ByteArray,
) : RecoveryAtomicFileAccess {
    private var bytes = initialBytes.copyOf()

    fun reset(source: ByteArray) {
        bytes = source.copyOf()
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

private fun reportMetadata() {
    println("schema,${RecoveryHostEvidenceReport.SCHEMA}")
    println("role,$CANDIDATE_ROLE")
    println("java_version,${System.getProperty("java.version")}")
    println("java_vm,${System.getProperty("java.vm.name")}")
    println("os,${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
    println("warmups,$WARMUP_COUNT")
    println("samples_per_group,$SAMPLE_COUNT")
    println("group,sample,latency_nanos")
    System.out.flush()
}

private fun reportSummaries(observations: List<RecoveryHostObservation>) {
    observations.groupBy(RecoveryHostObservation::group).forEach { (group, rows) ->
        println("summary_min,$group,${rows.minOf(RecoveryHostObservation::latencyNanos)}")
        println("summary_max,$group,${rows.maxOf(RecoveryHostObservation::latencyNanos)}")
    }
    System.out.flush()
}
