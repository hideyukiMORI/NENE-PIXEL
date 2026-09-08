package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

private const val WARMUP_COUNT: Int = 5
private const val SAMPLE_COUNT: Int = 20
private const val SAMPLE_ANOMALY_NANOS: Long = 1_000_000_000L

internal fun main() {
    val minimalDocument = ProjectFormatTestValues.minimalDocument
    val maximumDocument = ProjectFormatTestValues.maximumDocument()
    val minimalBytes =
        ProjectFormatTestValues.carrier(ProjectFormatTestValues.golden("minimal-v1.hex"))
    val maximumBytes = ProjectFormatV1Codec.encode(maximumDocument)
    check(ProjectFormatV1Codec.encode(minimalDocument) == minimalBytes)
    verifyCompleteRoundTrips(minimalDocument, minimalBytes, maximumDocument, maximumBytes)
    reportMetadata()

    val observations =
        buildList {
            addAll(measure("minimal_encode", { ProjectFormatV1Codec.encode(minimalDocument) }, ::verifyMinimalEncoding))
            addAll(
                measure(
                    "minimal_decode",
                    { ProjectFormatV1Codec.decode(minimalBytes) },
                    { result -> verifyAccepted(result, minimalDocument) },
                ),
            )
            addAll(measure("maximum_encode", { ProjectFormatV1Codec.encode(maximumDocument) }, ::verifyMaximumEncoding))
            addAll(
                measure(
                    "maximum_decode",
                    { ProjectFormatV1Codec.decode(maximumBytes) },
                    { result -> verifyAccepted(result, maximumDocument) },
                ),
            )
        }

    verifyCompleteRoundTrips(minimalDocument, minimalBytes, maximumDocument, maximumBytes)
    reportSummaries(observations)
}

private fun <T> measure(
    group: String,
    operation: () -> T,
    verify: (T) -> Unit,
): List<HostLatencyObservation> {
    repeat(WARMUP_COUNT) { verify(operation()) }
    return List(SAMPLE_COUNT) { sampleIndex ->
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

private fun verifyMinimalEncoding(bytes: ProjectFormatBytes) {
    check(bytes.byteCount == ProjectFormatV1Layout.MIN_FILE_BYTE_COUNT)
}

private fun verifyMaximumEncoding(bytes: ProjectFormatBytes) {
    check(bytes.byteCount == ProjectFormatBytes.MAX_FILE_BYTE_COUNT)
}

private fun verifyAccepted(
    result: ProjectFormatResult<DocumentState>,
    expected: DocumentState,
) {
    check(result is ProjectFormatResult.Accepted && result.value.id == expected.id)
}

private fun verifyCompleteRoundTrips(
    minimalDocument: DocumentState,
    minimalBytes: ProjectFormatBytes,
    maximumDocument: DocumentState,
    maximumBytes: ProjectFormatBytes,
) {
    check(accepted(ProjectFormatV1Codec.decode(minimalBytes)) == minimalDocument)
    check(accepted(ProjectFormatV1Codec.decode(maximumBytes)) == maximumDocument)
    verifyStoredChecksum(minimalBytes)
    verifyStoredChecksum(maximumBytes)
}

private fun verifyStoredChecksum(bytes: ProjectFormatBytes) {
    val checksumOffset = bytes.byteCount - Int.SIZE_BYTES
    val computed = Crc32IsoHdlc.checksum(bytes, checksumOffset)
    val stored = ProjectFormatBigEndian.readInt(bytes, checksumOffset).toUInt()
    check(computed == stored)
}

private fun accepted(result: ProjectFormatResult<DocumentState>): DocumentState =
    when (result) {
        is ProjectFormatResult.Accepted -> result.value
        is ProjectFormatResult.Rejected -> error("Host evidence decode was rejected: ${result.rejection}")
    }

private fun reportMetadata() {
    println("schema,nene-pixel-p3-project-format-host-latency-v1")
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

private data class HostLatencyObservation(
    val group: String,
    val sampleIndex: Int,
    val latencyNanos: Long,
)
