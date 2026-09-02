package io.github.hideyukimori.nenepixel.core.application.document.history

import java.nio.file.Files
import java.nio.file.Path

internal object P2RepresentationMeasurementReport {
    fun write(
        metrics: List<P2MeasurementMetric>,
        analyses: List<P2AnalysisRow>,
    ) {
        validateCurrentMetrics(metrics)
        val outputDirectory = System.getProperty(OUTPUT_DIRECTORY_PROPERTY)?.let(Path::of) ?: return
        Files.createDirectories(outputDirectory)
        writeRows(
            outputDirectory.resolve("host-current.csv"),
            currentMetadataRows() + metrics.flatMap(::metricRows) + analyses.map(::analysisRow),
        )
    }

    internal fun validateCurrentMetrics(metrics: List<P2MeasurementMetric>) {
        require(metrics.size == P2CurrentRawAcceptanceMatrix.CURRENT_METRIC_COUNT) {
            "Expected ${P2CurrentRawAcceptanceMatrix.CURRENT_METRIC_COUNT} current metrics, found ${metrics.size}."
        }
        require(
            metrics.sumOf { metric -> metric.samples.latenciesNanos.size } ==
                P2CurrentRawAcceptanceMatrix.CURRENT_RAW_SAMPLE_COUNT,
        ) {
            "Current report raw-sample count does not match schema v5."
        }
        require(
            metrics.all { metric ->
                metric.samples.latenciesNanos.size == metric.samples.allocatedBytes.size &&
                    metric.samples.latenciesNanos.size == metric.descriptor.sampling.sampleCount
            },
        ) {
            "Current report contains a metric with incomplete or mismatched raw samples."
        }
        P2CurrentRawAcceptanceMatrix.validateMetrics(
            metrics.filter { metric -> metric.descriptor.name == P2CurrentRawAcceptanceMatrix.METRIC_NAME },
        )
    }

    private fun currentMetadataRows(): List<String> =
        metadataRows("nene-pixel-p2-representation-limits-host-current-v5") +
            metadataRow(
                "current_boundary",
                "executed current representation including duplicate/no-op reference-clear fixtures, " +
                    "shuffled patch create, late conflict, and logical retained analysis; " +
                    "retained heap, ART, PSS, and rendering are not measured",
            ) +
            metadataRow(
                "current_raw_acceptance_boundary",
                "Stroke.create through exactly one rasterizeStroke call; valid containment scan, defensive copy, " +
                    "duplicate collapse, source filter, canonical changes, and PixelPatch.create included; " +
                    "fixture, apply, inverse, replay, verification, report, gateway, PixelSurface, ChangeSet, " +
                    "and history excluded",
            ) +
            metadataRow(
                "current_raw_acceptance_matrix",
                "shapes=64x64|16x256|256x16|128x128|64x256|256x64|256x256; " +
                    "factors=1|2|4|8; P=F*N; U=C=N; D=P-N; unchanged=0; adjacent duplicate runs",
            ) +
            metadataRow(
                "current_raw_acceptance_order",
                "shape table order then ascending factor; " +
                    "input_order=row_major|paired_row_major|quadrupled_row_major|octupled_row_major",
            ) +
            metadataRow(
                "current_raw_acceptance_rows",
                "28 metrics and 280 samples; host-current-v5 total=49 metrics and 454 samples",
            ) +
            metadataRow(
                "current_raw_acceptance_exclusions",
                "valid-input acceptance only; no cap, rejection, supported limit, ART, retained heap, PSS, " +
                    "render, frame, " +
                    "compositor, candidate conversion, or product decision evidence",
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

    private const val OUTPUT_DIRECTORY_PROPERTY: String = "nene.p2.representation.measurement.outputDirectory"
    private const val HOST_PROFILE: String = "NENE-P2-REPRESENTATION-WINDOWS-I9-10850K-JBR21"
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
        )
}
