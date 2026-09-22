package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

private const val WARMUP_COUNT: Int = 5
private const val SAMPLE_COUNT: Int = 20
private const val SAMPLE_ANOMALY_NANOS: Long = 1_000_000_000L
private const val CANDIDATE_ROLE: String = "candidate"
private const val MAXIMUM_EDGE: Int = 256
private const val MAXIMUM_PIXEL_COUNT: Int = MAXIMUM_EDGE * MAXIMUM_EDGE

internal fun main(arguments: Array<String>) {
    require(arguments.contentEquals(arrayOf(CANDIDATE_ROLE))) { "Candidate host evidence requires sole role argument" }
    val fixture = ProjectFormatHostEvidenceFixture()
    reportMetadata()
    val observations = fixture.groups().flatMap { group -> group.measure() }
    reportSummaries(observations)
}

internal fun verifyProjectFormatHostEvidenceFixtures() {
    ProjectFormatHostEvidenceFixture().groups().forEach { group -> group.verifyOnly() }
}

private class ProjectFormatHostEvidenceFixture {
    private val legacy = maximumLegacySource()
    private val legacyBytes = ProjectFormatCodec.encodeLegacySource(legacy)
    private val minimum = ProjectFormatTestValues.minimalDocument
    private val minimumBytes = ProjectFormatCodec.encode(minimum)
    private val maximum = ProjectFormatTestValues.maximumDocument()
    private val maximumBytes = ProjectFormatCodec.encode(maximum)

    fun groups(): List<ProjectFormatHostEvidenceGroup<*>> =
        listOf(
            legacyEncodeGroup(),
            legacyDecodeGroup(),
            currentEncodeGroup("v2_min_encode", minimum, ProjectFormatV2Layout.MIN_FILE_BYTE_COUNT),
            currentDecodeGroup("v2_min_decode", minimum, minimumBytes),
            currentEncodeGroup("v2_max_encode", maximum, ProjectFormatV2Layout.MAX_FILE_BYTE_COUNT),
            currentDecodeGroup("v2_max_decode", maximum, maximumBytes),
        )

    private fun legacyEncodeGroup(): ProjectFormatHostEvidenceGroup<ProjectFormatBytes> =
        ProjectFormatHostEvidenceGroup(
            "v1_max_exact_original_encode",
            { ProjectFormatCodec.encodeLegacySource(legacy) },
            { bytes -> verifyEncoded(bytes, ProjectFormatBytes.MAX_FILE_BYTE_COUNT, ProjectFormatV1Layout.VERSION) },
            { verifyLegacyBoundary() },
        )

    private fun legacyDecodeGroup(): ProjectFormatHostEvidenceGroup<ProjectFormatResult<DocumentImportSource>> =
        ProjectFormatHostEvidenceGroup(
            "v1_max_decode_legacy",
            { ProjectFormatCodec.decode(legacyBytes) },
            { result -> verifyLegacyFacts(result, legacy) },
            { verifyLegacyBoundary() },
        )

    private fun currentEncodeGroup(
        name: String,
        document: DocumentState,
        byteCount: Int,
    ): ProjectFormatHostEvidenceGroup<ProjectFormatBytes> =
        ProjectFormatHostEvidenceGroup(
            name,
            { ProjectFormatCodec.encode(document) },
            { bytes -> verifyEncoded(bytes, byteCount, ProjectFormatV2Layout.VERSION) },
            { verifyCurrentBoundary(document, ProjectFormatCodec.encode(document)) },
        )

    private fun currentDecodeGroup(
        name: String,
        document: DocumentState,
        bytes: ProjectFormatBytes,
    ): ProjectFormatHostEvidenceGroup<ProjectFormatResult<DocumentImportSource>> =
        ProjectFormatHostEvidenceGroup(
            name,
            { ProjectFormatCodec.decode(bytes) },
            { result -> verifyCurrentFacts(result, document) },
            { verifyCurrentBoundary(document, bytes) },
        )

    private fun verifyLegacyBoundary() {
        check(legacy.copyPackedRgba8888().toSet().size == MAXIMUM_PIXEL_COUNT)
        check(ProjectFormatCodec.encodeLegacySource(legacy) == legacyBytes)
        check(acceptedLegacy(ProjectFormatCodec.decode(legacyBytes)) == legacy)
        verifyStoredChecksum(legacyBytes)
    }
}

private class ProjectFormatHostEvidenceGroup<T>(
    private val name: String,
    private val operation: () -> T,
    private val verifyFacts: (T) -> Unit,
    private val verifyBoundary: () -> Unit,
) {
    fun verifyOnly() {
        verifyBoundary()
    }

    fun measure(): List<ProjectFormatHostObservation> {
        verifyBoundary()
        repeat(WARMUP_COUNT) { verifyFacts(operation()) }
        val observations = List(SAMPLE_COUNT, ::measureSample)
        verifyBoundary()
        return observations
    }

    private fun measureSample(index: Int): ProjectFormatHostObservation {
        val start = System.nanoTime()
        val result = operation()
        val elapsed = System.nanoTime() - start
        val observation = ProjectFormatHostObservation(name, index, elapsed)
        println(ProjectFormatHostEvidenceReport.sampleRow(observation))
        System.out.flush()
        verifyFacts(result)
        check(elapsed <= SAMPLE_ANOMALY_NANOS) { "$name sample $index exceeded one second" }
        return observation
    }
}

internal object ProjectFormatHostEvidenceReport {
    const val SCHEMA: String = "nene-pixel-p4-project-format-host-v1"
    val GROUPS: List<String> =
        listOf(
            "v1_max_exact_original_encode",
            "v1_max_decode_legacy",
            "v2_min_encode",
            "v2_min_decode",
            "v2_max_encode",
            "v2_max_decode",
        )

    fun sampleRow(observation: ProjectFormatHostObservation): String =
        "${observation.group},${observation.sampleIndex},${observation.latencyNanos}"

    fun parseSampleRow(row: String): ProjectFormatHostObservation {
        val fields = row.split(',')
        require(fields.size == 3 && fields[0] in GROUPS)
        val sample = fields[1].toInt()
        val latency = fields[2].toLong()
        require(sample in 0 until SAMPLE_COUNT && latency >= 0L)
        return ProjectFormatHostObservation(fields[0], sample, latency)
    }
}

internal data class ProjectFormatHostObservation(
    val group: String,
    val sampleIndex: Int,
    val latencyNanos: Long,
)

private fun maximumLegacySource(): LegacyRgbaSource {
    val size = canvas(MAXIMUM_EDGE)
    val pixels = IntArray(MAXIMUM_PIXEL_COUNT) { position -> position shl Byte.SIZE_BITS or 0xff }
    return created(
        LegacyRgbaSource.createPackedRgba8888(
            created(DocumentId.create("f0e0d0c0b0a090807060504030201000")),
            created(Revision.create(Long.MAX_VALUE)),
            size,
            pixels,
        ),
    )
}

private fun verifyEncoded(
    bytes: ProjectFormatBytes,
    expectedByteCount: Int,
    expectedVersion: Int,
) {
    check(bytes.byteCount == expectedByteCount)
    check(ProjectFormatBigEndian.readUnsignedShort(bytes, ProjectFormatV1Layout.VERSION_OFFSET) == expectedVersion)
}

private fun verifyLegacyFacts(
    result: ProjectFormatResult<DocumentImportSource>,
    expected: LegacyRgbaSource,
) {
    val source = acceptedLegacy(result)
    check(source.id == expected.id && source.revision == expected.revision && source.size == expected.size)
}

private fun verifyCurrentFacts(
    result: ProjectFormatResult<DocumentImportSource>,
    expected: DocumentState,
) {
    val document = acceptedCurrent(result)
    check(document.id == expected.id && document.revision == expected.revision && document.size == expected.size)
    check(document.definition.palette.entryCount == expected.definition.palette.entryCount)
    check(document.definition.defaultIndex == expected.definition.defaultIndex)
}

private fun verifyCurrentBoundary(
    document: DocumentState,
    bytes: ProjectFormatBytes,
) {
    check(ProjectFormatCodec.encode(document) == bytes)
    check(acceptedCurrent(ProjectFormatCodec.decode(bytes)) == document)
    verifyStoredChecksum(bytes)
}

private fun verifyStoredChecksum(bytes: ProjectFormatBytes) {
    val checksumOffset = bytes.byteCount - Int.SIZE_BYTES
    val computed = Crc32IsoHdlc.checksum(bytes, checksumOffset)
    val stored = ProjectFormatBigEndian.readInt(bytes, checksumOffset).toUInt()
    check(computed == stored)
}

private fun acceptedLegacy(result: ProjectFormatResult<DocumentImportSource>): LegacyRgbaSource =
    when (result) {
        is ProjectFormatResult.Accepted -> (result.value as DocumentImportSource.Legacy).source
        is ProjectFormatResult.Rejected -> error("Host evidence decode was rejected: ${result.rejection}")
    }

private fun acceptedCurrent(result: ProjectFormatResult<DocumentImportSource>): DocumentState =
    when (result) {
        is ProjectFormatResult.Accepted -> (result.value as DocumentImportSource.Current).document
        is ProjectFormatResult.Rejected -> error("Host evidence decode was rejected: ${result.rejection}")
    }

private fun reportMetadata() {
    println("schema,${ProjectFormatHostEvidenceReport.SCHEMA}")
    println("role,$CANDIDATE_ROLE")
    println("java_version,${System.getProperty("java.version")}")
    println("java_vm,${System.getProperty("java.vm.name")}")
    println("os,${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
    println("warmups,$WARMUP_COUNT")
    println("samples_per_group,$SAMPLE_COUNT")
    println("group,sample,latency_nanos")
    System.out.flush()
}

private fun reportSummaries(observations: List<ProjectFormatHostObservation>) {
    observations.groupBy(ProjectFormatHostObservation::group).forEach { (group, rows) ->
        println("summary_min,$group,${rows.minOf(ProjectFormatHostObservation::latencyNanos)}")
        println("summary_max,$group,${rows.maxOf(ProjectFormatHostObservation::latencyNanos)}")
    }
    System.out.flush()
}

private fun canvas(edge: Int): CanvasSize =
    CanvasSize.create(created(CanvasWidth.create(edge)), created(CanvasHeight.create(edge)))

private fun <T> created(result: DomainValueResult<T>): T =
    when (result) {
        is DomainValueResult.Created -> result.value
        is DomainValueResult.Rejected -> error("Invalid host evidence fixture: ${result.rejection}")
    }
