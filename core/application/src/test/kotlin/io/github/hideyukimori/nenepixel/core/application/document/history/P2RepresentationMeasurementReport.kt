package io.github.hideyukimori.nenepixel.core.application.document.history

import java.nio.file.Files
import java.nio.file.Path

internal object P2RepresentationMeasurementReport {
    fun write(
        metrics: List<P2MeasurementMetric>,
        analyses: List<P2AnalysisRow>,
    ) {
        val outputDirectory = System.getProperty(OUTPUT_DIRECTORY_PROPERTY)?.let(Path::of) ?: return
        Files.createDirectories(outputDirectory)
        writeRows(
            outputDirectory.resolve("host-current.csv"),
            currentMetadataRows() + metrics.flatMap(::metricRows),
        )
        writeRows(
            outputDirectory.resolve("host-candidates.csv"),
            candidateMetadataRows() + analyses.map(::analysisRow),
        )
    }

    private fun currentMetadataRows(): List<String> =
        metadataRows("nene-pixel-p2-representation-limits-host-current-v1") +
            metadataRow(
                "current_boundary",
                "executed current representation; retained heap, ART, PSS, and rendering are not measured",
            )

    private fun candidateMetadataRows(): List<String> =
        metadataRows("nene-pixel-p2-representation-limits-host-candidates-v1") +
            metadataRow(
                "analysis_boundary",
                "logical current-representation records only; analysis rows are not retained-byte measurements",
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
            csvRow(
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
            csvRow(
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
            csvRow(
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
            csvRow(
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
