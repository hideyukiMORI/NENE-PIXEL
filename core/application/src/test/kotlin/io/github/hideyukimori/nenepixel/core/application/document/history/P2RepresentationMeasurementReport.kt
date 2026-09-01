package io.github.hideyukimori.nenepixel.core.application.document.history

import java.nio.file.Files
import java.nio.file.Path

private data class P2CandidateReportSample(
    val recordType: String,
    val index: Int?,
    val latencyNanos: Long?,
    val allocatedBytes: Long?,
)

internal object P2RepresentationMeasurementReport {
    fun write(
        metrics: List<P2MeasurementMetric>,
        analyses: List<P2AnalysisRow>,
        candidates: List<P2CandidateMeasurementMetric>,
    ) {
        val outputDirectory = System.getProperty(OUTPUT_DIRECTORY_PROPERTY)?.let(Path::of) ?: return
        Files.createDirectories(outputDirectory)
        writeRows(
            outputDirectory.resolve("host-current.csv"),
            currentMetadataRows() + metrics.flatMap(::metricRows),
        )
        writeRows(
            outputDirectory.resolve("host-candidates.csv"),
            candidateMetadataRows() + candidates.flatMap(::candidateMetricRows) + analyses.map(::analysisRow),
        )
    }

    private fun currentMetadataRows(): List<String> =
        metadataRows("nene-pixel-p2-representation-limits-host-current-v2") +
            metadataRow(
                "current_boundary",
                "executed current representation; retained heap, ART, PSS, and rendering are not measured",
            )

    private fun candidateMetadataRows(): List<String> =
        metadataRows("nene-pixel-p2-representation-limits-host-candidates-v2") +
            listOf(
                metadataRow(
                    "analysis_boundary",
                    "executed test-only snapshot/surface candidates plus logical current-representation analysis; " +
                        "neither is retained-byte or physical evidence",
                ),
                metadataRow(
                    "candidate_matrix",
                    "64|128|256 square; high-entropy RGBA; snapshot build|one-pixel apply|dense apply; " +
                        "5 warmups and 10 diagnostic samples",
                ),
                metadataRow(
                    "semantic_oracle",
                    "independent row-major PixelColor values; all candidate semantic and inverse digests must match",
                ),
                metadataRow(
                    "driver_patch_boundary",
                    "common packed test-driver positions/before/after payload; " +
                        "candidate patch/inverse layout not measured",
                ),
                metadataRow(
                    "candidate_gaps",
                    "rectangles, full path/content/history matrix, candidate patch layout, retained heap, ART, PSS, " +
                        "frames, and semantic selection remain pending",
                ),
                metadataRow(
                    "palette_status",
                    "U8 value-palette pack/index correctness and 257-color typed rejection tested; " +
                        "performance comparison blocked on palette semantic ownership",
                ),
            )

    private fun metadataRows(schema: String): List<String> =
        listOf(
            csvRow(*REPORT_COLUMNS.toTypedArray()),
            metadataRow("schema", schema),
            metadataRow("profile", HOST_PROFILE),
            metadataRow("os", systemDescription()),
            metadataRow("jvm", jvmDescription()),
            metadataRow("worker_heap", "-Xms512m -Xmx512m"),
            metadataRow(
                "allocation_boundary",
                "current HotSpot test thread allocated bytes; retained heap, ART, PSS, and rendering excluded",
            ),
            metadataRow(
                "percentile_boundary",
                "p99 is the maximum of 10 or 7 diagnostic samples; " +
                    "hard-limit evidence requires the physical-profile protocol",
            ),
        )

    private fun writeRows(
        output: Path,
        rows: List<String>,
    ) {
        Files.writeString(output, rows.joinToString(System.lineSeparator(), postfix = System.lineSeparator()))
    }

    private fun metricRows(metric: P2MeasurementMetric): List<String> =
        listOf(summaryRow(metric)) +
            metric.samples.latenciesNanos.indices
                .map { index -> sampleRow(metric, index) }

    private fun summaryRow(metric: P2MeasurementMetric): String =
        metric.descriptor.run {
            reportRow(
                "metric",
                name,
                "",
                "measured",
                workload.canvas.width,
                workload.canvas.height,
                pixelCount,
                workload.pathPositions,
                workload.changeCount,
                workload.historyEntries,
                totalRetainedChanges,
                sampling.warmupIterations,
                sampling.sampleCount,
                "",
                "",
                "",
                metric.latency.median,
                metric.latency.p95,
                metric.latency.p99,
                metric.allocation.median,
                metric.allocation.p95,
                metric.allocation.p99,
                boundary,
            )
        }

    private fun sampleRow(
        metric: P2MeasurementMetric,
        index: Int,
    ): String =
        metric.descriptor.run {
            reportRow(
                "sample",
                name,
                "",
                "measured",
                workload.canvas.width,
                workload.canvas.height,
                pixelCount,
                workload.pathPositions,
                workload.changeCount,
                workload.historyEntries,
                totalRetainedChanges,
                sampling.warmupIterations,
                sampling.sampleCount,
                index,
                metric.samples.latenciesNanos[index],
                metric.samples.allocatedBytes[index],
                "",
                "",
                "",
                "",
                "",
                "",
                boundary,
            )
        }

    private fun candidateMetricRows(metric: P2CandidateMeasurementMetric): List<String> =
        listOf(candidateSummaryRow(metric)) +
            metric.samples.latenciesNanos
                .indices
                .map { index -> candidateSampleRow(metric, index) }

    private fun candidateSummaryRow(metric: P2CandidateMeasurementMetric): String =
        candidateRow(
            metric = metric,
            sample = P2CandidateReportSample("metric", null, null, null),
        )

    private fun candidateSampleRow(
        metric: P2CandidateMeasurementMetric,
        index: Int,
    ): String =
        candidateRow(
            metric = metric,
            sample =
                P2CandidateReportSample(
                    "sample",
                    index,
                    metric.samples.latenciesNanos[index],
                    metric.samples.allocatedBytes[index],
                ),
        )

    private fun candidateRow(
        metric: P2CandidateMeasurementMetric,
        sample: P2CandidateReportSample,
    ): String {
        val descriptor = metric.descriptor
        val outcome = metric.outcome
        val sampleValues =
            if (sample.index == null) {
                candidateSummaryValues(metric)
            } else {
                listOf(
                    "sample_index" to sample.index,
                    "latency_ns" to requireNotNull(sample.latencyNanos),
                    "allocated_bytes" to requireNotNull(sample.allocatedBytes),
                )
            }
        return rowByColumn(
            *candidateBaseValues(metric, sample.recordType).toTypedArray(),
            *sampleValues.toTypedArray(),
            "candidate_id" to descriptor.representation.candidateId,
            "operation_kind" to descriptor.operation.csvName,
            "content_kind" to P2CandidateContentKind.HighEntropyRgba.csvName,
            "path_kind" to descriptor.pathKind.csvName,
            "color_cardinality" to descriptor.canvas.pixelCount,
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

    private fun candidateSummaryValues(metric: P2CandidateMeasurementMetric): List<Pair<String, Any>> =
        listOf(
            "latency_median_ns" to metric.percentiles.latency.median,
            "latency_p95_ns" to metric.percentiles.latency.p95,
            "latency_p99_ns" to metric.percentiles.latency.p99,
            "allocated_median_bytes" to metric.percentiles.allocation.median,
            "allocated_p95_bytes" to metric.percentiles.allocation.p95,
            "allocated_p99_bytes" to metric.percentiles.allocation.p99,
        )

    private fun analysisRow(row: P2AnalysisRow): String =
        when (row) {
            is P2AnalysisRow.RetainedStructure -> retainedStructureRow(row)
            is P2AnalysisRow.ExcludedCandidate -> excludedCandidateRow(row)
        }

    private fun retainedStructureRow(row: P2AnalysisRow.RetainedStructure): String =
        row.descriptor.run {
            reportRow(
                "analysis",
                name,
                "snapshot_reference_slots=${row.counts.snapshotPixelReferenceSlots};" +
                    "forward_change_records=${row.counts.forwardChangeRecords};" +
                    "inverse_change_records=${row.counts.inverseChangeRecords}",
                "analytical_structure",
                canvas.width,
                canvas.height,
                canvas.pixelCount,
                "",
                retained.changeCount,
                retained.historyEntries,
                retained.totalChanges,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                boundary,
            )
        }

    private fun excludedCandidateRow(row: P2AnalysisRow.ExcludedCandidate): String =
        row.descriptor.run {
            reportRow(
                "analysis",
                name,
                row.reason.name,
                "excluded",
                canvas.width,
                canvas.height,
                canvas.pixelCount,
                "",
                retained.changeCount,
                retained.historyEntries,
                retained.totalChanges,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                boundary,
            )
        }

    private fun metadataRow(
        name: String,
        value: String,
    ): String =
        csvRow(
            "metadata",
            name,
            value,
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
        )

    private fun systemDescription(): String =
        listOf("os.name", "os.version", "os.arch").joinToString(" ") { property ->
            requiredSystemProperty(property)
        }

    private fun jvmDescription(): String =
        listOf("java.vm.name", "java.runtime.version").joinToString(" ") { property ->
            requiredSystemProperty(property)
        }

    private fun requiredSystemProperty(name: String): String =
        requireNotNull(System.getProperty(name)) { "Required JVM system property '$name' is unavailable." }

    private fun csvRow(vararg values: Any): String =
        values.joinToString(",") { value -> "\"${value.toString().replace("\"", "\"\"")}\"" }

    private fun reportRow(vararg values: Any): String {
        require(values.size <= REPORT_COLUMNS.size) { "Too many representation report values." }
        return csvRow(*values, *Array(REPORT_COLUMNS.size - values.size) { "" })
    }

    private fun rowByColumn(vararg values: Pair<String, Any>): String {
        val valuesByColumn = values.toMap()
        check(valuesByColumn.keys.all(REPORT_COLUMNS::contains)) { "Unknown representation report column." }
        return csvRow(*REPORT_COLUMNS.map { column -> valuesByColumn[column] ?: "" }.toTypedArray())
    }

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
