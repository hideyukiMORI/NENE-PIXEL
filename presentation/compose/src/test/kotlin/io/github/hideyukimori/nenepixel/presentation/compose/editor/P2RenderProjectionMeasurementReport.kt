package io.github.hideyukimori.nenepixel.presentation.compose.editor

import java.nio.file.Files
import java.nio.file.Path

internal object P2RenderProjectionMeasurementReport {
    fun write(metrics: List<P2RenderProjectionMetric>) {
        validate(metrics)
        val outputDirectory = System.getProperty(OUTPUT_DIRECTORY_PROPERTY)?.let(Path::of) ?: return
        Files.createDirectories(outputDirectory)
        val rows = metadataRows() + metrics.flatMap(::metricRows)
        Files.writeString(
            outputDirectory.resolve(OUTPUT_FILE_NAME),
            rows.joinToString(System.lineSeparator(), postfix = System.lineSeparator()),
        )
    }

    private fun validate(metrics: List<P2RenderProjectionMetric>) {
        P2RenderProjectionMatrix.validate(metrics.map(P2RenderProjectionMetric::descriptor))
        val rawSampleCount = metrics.sumOf { metric -> metric.measurement.samples.latenciesNanos.size }
        check(rawSampleCount == P2RenderProjectionMatrix.RAW_SAMPLE_COUNT) {
            "Host render-projection raw sample count changed."
        }
        check(metrics.all(::hasCompleteSamples)) { "Host render-projection raw sample pairs are incomplete." }
        check(metrics.count { metric -> metric.measurement.deterministicKey.status != PASS } == 0) {
            "Host render-projection correctness failure count changed."
        }
    }

    private fun hasCompleteSamples(metric: P2RenderProjectionMetric): Boolean =
        metric.measurement.samples.run {
            latenciesNanos.size == P2RenderProjectionMatrix.SAMPLE_COUNT &&
                allocatedBytes.size == P2RenderProjectionMatrix.SAMPLE_COUNT
        }

    private fun metadataRows(): List<String> =
        listOf(
            P2HostMeasurementReport.csvRow(*REPORT_COLUMNS.toTypedArray()),
            metadataRow("schema", SCHEMA),
            metadataRow("profile", HOST_PROFILE),
            metadataRow("os", P2HostMeasurementReport.systemDescription()),
            metadataRow("jvm", P2HostMeasurementReport.jvmDescription()),
            metadataRow("worker_heap", "-Xms512m -Xmx512m"),
            metadataRow("build_variant", "presentation-debug-host-unit-test-worker"),
            metadataRow("metric_count", P2RenderProjectionMatrix.METRIC_COUNT.toString()),
            metadataRow("raw_sample_count", P2RenderProjectionMatrix.RAW_SAMPLE_COUNT.toString()),
            metadataRow("correctness_failure_count", "0"),
            metadataRow("sampling", "5 warmups and 10 diagnostic samples per shape/content metric"),
            metadataRow("shape_matrix", shapeMatrix),
            metadataRow("content_matrix", contentMatrix),
            metadataRow("operation_boundary", OPERATION_BOUNDARY),
            metadataRow("exclusions", EXCLUSIONS),
            metadataRow("allocation_boundary", ALLOCATION_BOUNDARY),
            metadataRow("percentile_boundary", PERCENTILE_BOUNDARY),
            metadataRow("oracle_boundary", ORACLE_BOUNDARY),
            metadataRow("source_digest_encoding", SOURCE_DIGEST_ENCODING),
            metadataRow("projection_digest_encoding", PROJECTION_DIGEST_ENCODING),
        )

    private fun metricRows(metric: P2RenderProjectionMetric): List<String> =
        listOf(summaryRow(metric)) +
            metric.measurement.samples.latenciesNanos.indices
                .map { index -> sampleRow(metric, index) }

    private fun summaryRow(metric: P2RenderProjectionMetric): String =
        rowByColumn(
            *baseValues(metric, "metric").toTypedArray(),
            "latency_median_ns" to metric.measurement.latency.median,
            "latency_p95_ns" to metric.measurement.latency.p95,
            "latency_p99_ns" to metric.measurement.latency.p99,
            "allocated_median_bytes" to metric.measurement.allocation.median,
            "allocated_p95_bytes" to metric.measurement.allocation.p95,
            "allocated_p99_bytes" to metric.measurement.allocation.p99,
        )

    private fun sampleRow(
        metric: P2RenderProjectionMetric,
        index: Int,
    ): String =
        rowByColumn(
            *baseValues(metric, "sample").toTypedArray(),
            "sample_index" to index,
            "latency_ns" to metric.measurement.samples.latenciesNanos[index],
            "allocated_bytes" to metric.measurement.samples.allocatedBytes[index],
        )

    private fun baseValues(
        metric: P2RenderProjectionMetric,
        recordType: String,
    ): List<Pair<String, Any>> {
        val descriptor = metric.descriptor
        val correctness = metric.measurement.deterministicKey
        return listOf(
            "record_type" to recordType,
            "name" to METRIC_NAME,
            "status" to "measured_current",
            "canvas_width" to descriptor.shape.width,
            "canvas_height" to descriptor.shape.height,
            "pixel_count" to descriptor.shape.pixelCount,
            "content_kind" to descriptor.content.csvName,
            "content_description" to descriptor.content.description,
            "color_cardinality" to metric.colorCardinality,
            "warmup" to P2RenderProjectionMatrix.WARMUP_ITERATIONS,
            "samples" to P2RenderProjectionMatrix.SAMPLE_COUNT,
            "source_revision" to correctness.sourceRevision,
            "source_pixel_digest_sha256" to correctness.sourcePixelDigest,
            "projection_digest_sha256" to correctness.projectionDigest,
            "first_x" to 0,
            "first_y" to 0,
            "first_argb_aarrggbb" to P2RenderProjectionDigest.argbHex(metric.firstArgb),
            "last_x" to (descriptor.shape.width - 1),
            "last_y" to (descriptor.shape.height - 1),
            "last_argb_aarrggbb" to P2RenderProjectionDigest.argbHex(metric.lastArgb),
            "correctness_status" to correctness.status,
            "boundary" to OPERATION_BOUNDARY,
        )
    }

    private fun metadataRow(
        name: String,
        value: String,
    ): String = P2HostMeasurementReport.metadataRow(REPORT_COLUMNS.size, name, value)

    private fun rowByColumn(vararg values: Pair<String, Any>): String {
        val names = values.map(Pair<String, Any>::first)
        check(names.size == names.toSet().size) { "Duplicate host render-projection report column." }
        val byName = values.toMap()
        check(byName.keys.all(REPORT_COLUMNS::contains)) { "Unknown host render-projection report column." }
        return P2HostMeasurementReport.csvRow(*REPORT_COLUMNS.map { column -> byName[column] ?: "" }.toTypedArray())
    }

    private const val OUTPUT_DIRECTORY_PROPERTY: String = "nene.p2.projection.measurement.outputDirectory"
    private const val OUTPUT_FILE_NAME: String = "host-projection.csv"
    private const val SCHEMA: String = "nene-pixel-p2-representation-limits-host-projection-v1"
    private const val HOST_PROFILE: String = "NENE-P2-REPRESENTATION-WINDOWS-I9-10850K-JBR21"
    private const val METRIC_NAME: String = "current_snapshot_to_rendered_pixels"
    private const val PASS: String = "pass"
    private const val OPERATION_BOUNDARY: String =
        "one prepared current PixelSnapshot.toRenderedPixels() call; fixture, oracle, digest, and verification excluded"
    private const val EXCLUSIONS: String =
        "draw iteration, viewport clipping, Compose scheduling, Android ART, retained heap, PSS, GPU/compositor, frame"
    private const val ALLOCATION_BOUNDARY: String =
        "current HotSpot presentation test thread allocated bytes for projection; retained heap, ART, PSS, " +
            "draw iteration, and frame rendering excluded"
    private const val PERCENTILE_BOUNDARY: String =
        "nearest-rank median, p95, p99; p99 is the maximum of 10 diagnostic samples and is not physical tail evidence"
    private const val ORACLE_BOUNDARY: String =
        "all N outputs independently checked for count,row-major x/y,exact AARRGGBB,sRGB,endpoints,full digest; " +
            "source revision and pixel digest checked before and after every warmup/sample"
    private const val SOURCE_DIGEST_ENCODING: String =
        "SHA-256 over row-major AARRGGBB Int values encoded big-endian; uppercase hexadecimal"
    private const val PROJECTION_DIGEST_ENCODING: String =
        "SHA-256 over output count then each row-major x,y,AARRGGBB Int encoded big-endian; uppercase hexadecimal"
    private val shapeMatrix: String =
        P2RenderProjectionMatrix.shapes.joinToString("|") { shape -> shape.csvName }
    private val contentMatrix: String =
        P2RenderProjectionContent.entries.joinToString("|") { content -> content.csvName }
    private val REPORT_COLUMNS: List<String> =
        listOf(
            "record_type",
            "name",
            "value",
            "status",
            "canvas_width",
            "canvas_height",
            "pixel_count",
            "content_kind",
            "content_description",
            "color_cardinality",
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
            "source_revision",
            "source_pixel_digest_sha256",
            "projection_digest_sha256",
            "first_x",
            "first_y",
            "first_argb_aarrggbb",
            "last_x",
            "last_y",
            "last_argb_aarrggbb",
            "correctness_status",
            "boundary",
        )
}
