package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

import java.nio.file.Files
import java.nio.file.Path

internal object P2CandidateMeasurementReport {
    fun write(candidates: List<P2CandidateMeasurementMetric>) {
        val outputDirectory = System.getProperty(OUTPUT_DIRECTORY_PROPERTY)?.let(Path::of) ?: return
        Files.createDirectories(outputDirectory)
        val rows = metadataRows() + candidates.flatMap(::candidateMetricRows)
        Files.writeString(
            outputDirectory.resolve("host-candidates.csv"),
            rows.joinToString(System.lineSeparator(), postfix = System.lineSeparator()),
        )
    }

    private fun metadataRows(): List<String> = baseMetadataRows() + contractMetadataRows()

    private fun baseMetadataRows(): List<String> =
        listOf(
            csvRow(*REPORT_COLUMNS.toTypedArray()),
            metadataRow("schema", "nene-pixel-p2-representation-limits-host-candidates-v3"),
            metadataRow("profile", HOST_PROFILE),
            metadataRow("os", systemDescription()),
            metadataRow("jvm", jvmDescription()),
            metadataRow("worker_heap", "-Xms512m -Xmx512m"),
            metadataRow(
                "allocation_boundary",
                "current HotSpot pixel-engine test thread allocated bytes; " +
                    "retained heap, ART, PSS, and rendering excluded",
            ),
            metadataRow(
                "percentile_boundary",
                "p99 is the maximum of 10 diagnostic samples; " +
                    "hard-limit evidence requires the physical-profile protocol",
            ),
        )

    private fun contractMetadataRows(): List<String> =
        listOf(
            metadataRow(
                "analysis_boundary",
                "executed test-only snapshot/surface candidates inside the ARC-005 pixel-engine test boundary; " +
                    "not retained-byte or physical evidence",
            ),
            metadataRow(
                "candidate_matrix",
                "64x64|16x256|256x16|128x128|64x256|256x64|256x256; " +
                    "one|256|high-entropy RGBA snapshot build; high-entropy one|diagonal|row|column|" +
                    "25%|50%|100% apply; 5 warmups and 10 diagnostic samples",
            ),
            metadataRow(
                "semantic_oracle",
                "independent row-major PixelColor values; all candidate semantic and inverse digests must match",
            ),
            metadataRow(
                "driver_patch_boundary",
                "common packed test-driver positions/before/after payload; candidate patch/inverse layout not measured",
            ),
            metadataRow(
                "candidate_gaps",
                "duplicate/no-op/reference-clear paths, candidate patch layout, retained history, heap, ART, PSS, " +
                    "frames, and semantic selection remain pending",
            ),
            metadataRow(
                "palette_status",
                "U8 value-palette pack/index correctness and 257-color typed rejection tested; " +
                    "performance comparison blocked on palette semantic ownership",
            ),
        )

    private fun candidateMetricRows(metric: P2CandidateMeasurementMetric): List<String> =
        listOf(candidateRow(metric, P2CandidateReportSample("metric", null, null, null))) +
            metric.samples.latenciesNanos.indices.map { index ->
                candidateRow(
                    metric,
                    P2CandidateReportSample(
                        recordType = "sample",
                        index = index,
                        latencyNanos = metric.samples.latenciesNanos[index],
                        allocatedBytes = metric.samples.allocatedBytes[index],
                    ),
                )
            }

    private fun candidateRow(
        metric: P2CandidateMeasurementMetric,
        sample: P2CandidateReportSample,
    ): String {
        val descriptor = metric.descriptor
        val outcome = metric.outcome
        return rowByColumn(
            *candidateBaseValues(metric, sample.recordType).toTypedArray(),
            *sample.values(metric).toTypedArray(),
            "candidate_id" to descriptor.representation.candidateId,
            "operation_kind" to descriptor.operation.csvName,
            "content_kind" to descriptor.operation.contentKind.csvName,
            "path_kind" to descriptor.pathKind.csvName,
            "color_cardinality" to descriptor.colorCardinality,
            "tile_edge" to outcome.units.tileEdge,
            "touched_units" to outcome.units.touched,
            "copied_units" to outcome.units.copied,
            "shared_units" to outcome.units.shared,
            "primitive_payload_bytes" to outcome.storage.primitivePayloadBytes,
            "reference_slots" to outcome.storage.referenceSlots,
            "copied_primitive_bytes" to outcome.storage.copiedPrimitiveBytes,
            "copied_reference_slots" to outcome.storage.copiedReferenceSlots,
            "driver_patch_payload_bytes" to outcome.patchPayloadBytes,
            "semantic_digest" to outcome.correctness.semanticDigest,
            "inverse_digest" to outcome.correctness.inverseDigest,
            "correctness_status" to outcome.correctness.status,
        )
    }

    private fun candidateBaseValues(
        metric: P2CandidateMeasurementMetric,
        recordType: String,
    ): List<Pair<String, Any>> {
        val descriptor = metric.descriptor
        return listOf(
            "record_type" to recordType,
            "name" to "p2_candidate_${descriptor.operation.csvName}",
            "status" to "measured_test_only",
            "canvas_width" to descriptor.canvas.width,
            "canvas_height" to descriptor.canvas.height,
            "pixel_count" to descriptor.canvas.pixelCount,
            "path_positions" to descriptor.changeCount,
            "change_count" to descriptor.changeCount,
            "history_entries" to if (descriptor.changeCount == 0) 0 else 1,
            "total_retained_changes" to descriptor.changeCount,
            "warmup" to CANDIDATE_WARMUPS,
            "samples" to CANDIDATE_SAMPLES,
            "boundary" to descriptor.boundary,
        )
    }

    private fun P2CandidateReportSample.values(metric: P2CandidateMeasurementMetric): List<Pair<String, Any>> =
        if (index == null) {
            listOf(
                "latency_median_ns" to metric.percentiles.latency.median,
                "latency_p95_ns" to metric.percentiles.latency.p95,
                "latency_p99_ns" to metric.percentiles.latency.p99,
                "allocated_median_bytes" to metric.percentiles.allocation.median,
                "allocated_p95_bytes" to metric.percentiles.allocation.p95,
                "allocated_p99_bytes" to metric.percentiles.allocation.p99,
            )
        } else {
            listOf(
                "sample_index" to index,
                "latency_ns" to requireNotNull(latencyNanos),
                "allocated_bytes" to requireNotNull(allocatedBytes),
            )
        }

    private fun metadataRow(
        name: String,
        value: String,
    ): String = rowByColumn("record_type" to "metadata", "name" to name, "value" to value)

    private fun rowByColumn(vararg values: Pair<String, Any>): String {
        val valuesByColumn = values.toMap()
        check(valuesByColumn.keys.all(REPORT_COLUMNS::contains)) { "Unknown candidate report column." }
        return csvRow(*REPORT_COLUMNS.map { column -> valuesByColumn[column] ?: "" }.toTypedArray())
    }

    private fun systemDescription(): String =
        listOf("os.name", "os.version", "os.arch").joinToString(" ", transform = ::requiredSystemProperty)

    private fun jvmDescription(): String =
        listOf("java.vm.name", "java.runtime.version").joinToString(" ", transform = ::requiredSystemProperty)

    private fun requiredSystemProperty(name: String): String =
        requireNotNull(System.getProperty(name)) { "Required JVM system property '$name' is unavailable." }

    private fun csvRow(vararg values: Any): String =
        values.joinToString(",") { value -> "\"${value.toString().replace("\"", "\"\"")}\"" }

    private const val OUTPUT_DIRECTORY_PROPERTY: String = "nene.p2.representation.measurement.outputDirectory"
    private const val HOST_PROFILE: String = "NENE-P2-REPRESENTATION-WINDOWS-I9-10850K-JBR21"
    private const val CANDIDATE_WARMUPS: Int = 5
    private const val CANDIDATE_SAMPLES: Int = 10
    private val REPORT_COLUMNS: List<String> =
        listOf(
            "record_type",
            "name",
            "value",
            "status",
            "canvas_width",
            "canvas_height",
            "pixel_count",
            "path_positions",
            "change_count",
            "history_entries",
            "total_retained_changes",
            "warmup",
            "samples",
            "sample_index",
            "latency_ns",
            "allocated_bytes",
            "latency_median_ns",
            "latency_p95_ns",
            "latency_p99_ns",
            "allocated_median_bytes",
            "allocated_p95_bytes",
            "allocated_p99_bytes",
            "boundary",
            "candidate_id",
            "operation_kind",
            "content_kind",
            "path_kind",
            "color_cardinality",
            "tile_edge",
            "touched_units",
            "copied_units",
            "shared_units",
            "primitive_payload_bytes",
            "reference_slots",
            "copied_primitive_bytes",
            "copied_reference_slots",
            "driver_patch_payload_bytes",
            "semantic_digest",
            "inverse_digest",
            "correctness_status",
        )
}

private data class P2CandidateReportSample(
    val recordType: String,
    val index: Int?,
    val latencyNanos: Long?,
    val allocatedBytes: Long?,
)
