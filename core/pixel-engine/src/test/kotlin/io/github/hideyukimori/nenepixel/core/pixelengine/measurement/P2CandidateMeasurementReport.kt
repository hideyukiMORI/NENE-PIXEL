package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

import java.nio.file.Files
import java.nio.file.Path

internal object P2CandidateMeasurementReport {
    fun write(
        candidates: List<P2CandidateMeasurementMetric>,
        patchCandidates: List<P2CandidatePatchMeasurementMetric>,
    ) {
        val outputDirectory = System.getProperty(OUTPUT_DIRECTORY_PROPERTY)?.let(Path::of) ?: return
        Files.createDirectories(outputDirectory)
        val rows =
            metadataRows() +
                candidates.flatMap(::candidateMetricRows) +
                patchCandidates.flatMap(::patchMetricRows)
        Files.writeString(
            outputDirectory.resolve("host-candidates.csv"),
            rows.joinToString(System.lineSeparator(), postfix = System.lineSeparator()),
        )
    }

    private fun metadataRows(): List<String> = baseMetadataRows() + contractMetadataRows()

    private fun baseMetadataRows(): List<String> =
        listOf(
            csvRow(*REPORT_COLUMNS.toTypedArray()),
            metadataRow("schema", "nene-pixel-p2-representation-limits-host-candidates-v4"),
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
                    "25%|50%|100% apply; 256x256-only dense shuffled create|inverse create|forward apply|" +
                    "inverse apply|round trip|late conflict; 5 warmups and 10 diagnostic samples",
            ),
            metadataRow(
                "semantic_oracle",
                "independent row-major PixelColor values; all candidate semantic and inverse digests must match",
            ),
        ) + patchMetadataRows()

    private fun patchMetadataRows(): List<String> =
        listOf(
            metadataRow(
                "patch_configuration_boundary",
                "snapshot candidate plus object/materialized or packed/shared-directional native patch; " +
                    "semantic input generation and full verification excluded from timing",
            ),
            metadataRow(
                "unit_boundary",
                "candidate snapshot/apply/standalone-patch rows execute no raw stroke or history retention; " +
                    "path_positions=0, history_entries=0, and change_count is the native patch size",
            ),
            metadataRow(
                "patch_logical_storage_boundary",
                "change-record primitive fields, color-reference fields, occupied list-element reference slots, " +
                    "object records, and owned primitive arrays only; wrapper/list/array headers, spare list " +
                    "capacity, and shared PixelColor referents excluded; not retained bytes",
            ),
            metadataRow(
                "candidate_gaps",
                "duplicate/no-op/reference-clear paths, retained history, heap, ART, PSS, frames, and semantic " +
                    "selection remain pending",
            ),
            metadataRow("cross_configuration_correctness", "all patch semantic and lifecycle digests matched"),
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

    private fun patchMetricRows(metric: P2CandidatePatchMeasurementMetric): List<String> =
        listOf(patchRow(metric, P2CandidateReportSample("metric", null, null, null))) +
            metric.samples.latenciesNanos.indices.map { index ->
                patchRow(
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
            *configurationValues(descriptor.configuration).toTypedArray(),
            "operation_kind" to descriptor.operation.csvName,
            "operation_boundary" to descriptor.boundary,
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
            *patchStorageValues(outcome.patchStorage).toTypedArray(),
            "result_kind" to if (descriptor.operation.isSnapshotBuild) "Built" else "Applied",
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
            "path_positions" to 0,
            "change_count" to descriptor.changeCount,
            "history_entries" to 0,
            "total_retained_changes" to 0,
            "warmup" to CANDIDATE_WARMUPS,
            "samples" to CANDIDATE_SAMPLES,
            "boundary" to descriptor.boundary,
        )
    }

    private fun patchRow(
        metric: P2CandidatePatchMeasurementMetric,
        sample: P2CandidateReportSample,
    ): String {
        val descriptor = metric.descriptor
        val outcome = metric.outcome
        return rowByColumn(
            *patchBaseValues(metric, sample.recordType).toTypedArray(),
            *sample.patchValues(metric).toTypedArray(),
            *configurationValues(descriptor.configuration).toTypedArray(),
            "operation_kind" to descriptor.operation.csvName,
            "operation_boundary" to descriptor.protocol.boundary,
            "content_kind" to "deterministic_high_entropy_rgba",
            "path_kind" to "standalone_patch",
            "color_cardinality" to descriptor.canvas.pixelCount,
            "tile_edge" to descriptor.configuration.snapshotRepresentation.reportTileEdge(),
            "primitive_payload_bytes" to outcome.storage.snapshot.primitivePayloadBytes,
            "reference_slots" to outcome.storage.snapshot.referenceSlots,
            *patchStorageValues(outcome.storage.patch).toTypedArray(),
            *patchResultValues(outcome.result).toTypedArray(),
            *patchStateValues(outcome.state).toTypedArray(),
            *patchCorrectnessValues(outcome.correctness).toTypedArray(),
            "execution_order" to descriptor.protocol.executionOrder,
            "input_order" to descriptor.protocol.inputOrder,
        )
    }

    private fun patchResultValues(result: P2CandidatePatchResultEvidence): List<Pair<String, Any>> =
        listOf(
            "result_kind" to result.resultKind,
            "rejection_kind" to result.rejectionKind,
            "conflict_position" to (result.conflictPosition ?: ""),
        )

    private fun patchStateValues(state: P2CandidatePatchStateEvidence): List<Pair<String, Any>> =
        regionValues(state.affectedRegion) +
            lifecycleValues(state.lifecycle) +
            operationValues(state.operation) +
            listOf(
                "unaffected_pixel_count" to 0,
                "unaffected_input_digest_sha256" to state.unaffected.inputDigest,
                "unaffected_output_digest_sha256" to state.unaffected.outputDigest,
            )

    private fun regionValues(region: P2CandidateAffectedRegion): List<Pair<String, Any>> =
        listOf(
            "affected_left" to region.left,
            "affected_top" to region.top,
            "affected_width" to region.width,
            "affected_height" to region.height,
        )

    private fun lifecycleValues(lifecycle: P2CandidatePatchLifecycleEvidence): List<Pair<String, Any>> =
        listOf(
            "expected_lifecycle_before_revision" to lifecycle.revisions.before,
            "expected_lifecycle_after_revision" to lifecycle.revisions.after,
            "expected_lifecycle_restored_revision" to lifecycle.revisions.restored,
            "expected_lifecycle_before_pixel_digest_sha256" to lifecycle.digests.before,
            "expected_lifecycle_after_pixel_digest_sha256" to lifecycle.digests.applied,
            "expected_lifecycle_restored_pixel_digest_sha256" to lifecycle.digests.restored,
        )

    private fun operationValues(operation: P2CandidatePatchOperationEvidence): List<Pair<String, Any>> =
        listOf(
            "operation_input_revision" to operation.inputRevision,
            "operation_output_revision" to operation.outputRevision,
            "operation_input_pixel_digest_sha256" to operation.inputPixelDigest,
            "operation_output_pixel_digest_sha256" to operation.outputPixelDigest,
            "operation_state_unchanged" to (
                operation.inputPixelDigest == operation.outputPixelDigest &&
                    operation.inputRevision == operation.outputRevision
            ),
        )

    private fun patchCorrectnessValues(correctness: P2CandidatePatchCorrectness): List<Pair<String, Any>> =
        listOf(
            "canonical_order_digest_sha256" to correctness.canonicalOrderDigest,
            "forward_patch_digest_sha256" to correctness.forwardPatchDigest,
            "inverse_patch_digest_sha256" to correctness.inversePatchDigest,
            "correctness_status" to correctness.status,
        )

    private fun patchBaseValues(
        metric: P2CandidatePatchMeasurementMetric,
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
            "path_positions" to 0,
            "change_count" to descriptor.canvas.pixelCount,
            "history_entries" to 0,
            "total_retained_changes" to 0,
            "warmup" to CANDIDATE_WARMUPS,
            "samples" to CANDIDATE_SAMPLES,
            "boundary" to descriptor.protocol.boundary,
        )
    }

    private fun configurationValues(configuration: P2CandidateConfiguration): List<Pair<String, Any>> =
        listOf(
            "configuration_id" to configuration.configurationId,
            "snapshot_candidate_id" to configuration.snapshotRepresentation.candidateId,
            "patch_candidate_id" to configuration.patchLayout.candidateId,
            "inverse_policy" to configuration.patchLayout.inversePolicy.csvName,
        )

    private fun patchStorageValues(storage: P2CandidatePatchPairStorage): List<Pair<String, Any>> =
        storageValues("forward_patch", storage.forward) +
            storageValues("inverse_additional", storage.inverseAdditional) +
            storageValues("shared_patch", storage.shared) +
            storageValues("retained_patch_union", storage.retainedUnion)

    private fun storageValues(
        prefix: String,
        storage: P2CandidatePatchStorageCounts,
    ): List<Pair<String, Any>> =
        listOf(
            "${prefix}_primitive_bytes" to storage.primitivePayloadBytes,
            "${prefix}_reference_slots" to storage.referenceSlots,
            "${prefix}_object_records" to storage.objectRecords,
            "${prefix}_primitive_backing_arrays" to storage.primitiveBackingArrays,
        )

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

    private fun P2CandidateReportSample.patchValues(
        metric: P2CandidatePatchMeasurementMetric,
    ): List<Pair<String, Any>> =
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

    private fun P2CandidateRepresentation.reportTileEdge(): Int =
        when (this) {
            P2CandidateRepresentation.TiledCowRgba8888T16 -> 16
            P2CandidateRepresentation.TiledCowRgba8888T32 -> 32
            P2CandidateRepresentation.TiledCowRgba8888T64 -> 64
            else -> 0
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
            "configuration_id",
            "snapshot_candidate_id",
            "patch_candidate_id",
            "inverse_policy",
            "operation_kind",
            "operation_boundary",
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
            "forward_patch_primitive_bytes",
            "forward_patch_reference_slots",
            "forward_patch_object_records",
            "forward_patch_primitive_backing_arrays",
            "inverse_additional_primitive_bytes",
            "inverse_additional_reference_slots",
            "inverse_additional_object_records",
            "inverse_additional_primitive_backing_arrays",
            "shared_patch_primitive_bytes",
            "shared_patch_reference_slots",
            "shared_patch_object_records",
            "shared_patch_primitive_backing_arrays",
            "retained_patch_union_primitive_bytes",
            "retained_patch_union_reference_slots",
            "retained_patch_union_object_records",
            "retained_patch_union_primitive_backing_arrays",
            "result_kind",
            "rejection_kind",
            "conflict_position",
            "unaffected_pixel_count",
            "affected_left",
            "affected_top",
            "affected_width",
            "affected_height",
            "expected_lifecycle_before_revision",
            "expected_lifecycle_after_revision",
            "expected_lifecycle_restored_revision",
            "expected_lifecycle_before_pixel_digest_sha256",
            "expected_lifecycle_after_pixel_digest_sha256",
            "expected_lifecycle_restored_pixel_digest_sha256",
            "operation_input_revision",
            "operation_output_revision",
            "operation_input_pixel_digest_sha256",
            "operation_output_pixel_digest_sha256",
            "operation_state_unchanged",
            "unaffected_input_digest_sha256",
            "unaffected_output_digest_sha256",
            "canonical_order_digest_sha256",
            "forward_patch_digest_sha256",
            "inverse_patch_digest_sha256",
            "execution_order",
            "input_order",
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
