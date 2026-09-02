package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

import java.nio.file.Files
import java.nio.file.Path

internal object P2CandidateMeasurementReport {
    fun write(
        candidates: List<P2CandidateMeasurementMetric>,
        patchCandidates: List<P2CandidatePatchMeasurementMetric>,
        rawPathCandidates: List<P2CandidateRawPathMeasurementMetric>,
        retainedHistoryCandidates: List<P2CandidateRetainedHistoryMeasurementMetric>,
    ) {
        assertReportMatrix(candidates, patchCandidates, rawPathCandidates, retainedHistoryCandidates)
        val outputDirectory = System.getProperty(OUTPUT_DIRECTORY_PROPERTY)?.let(Path::of) ?: return
        Files.createDirectories(outputDirectory)
        val rows =
            metadataRows() +
                candidates.flatMap(::candidateMetricRows) +
                patchCandidates.flatMap(::patchMetricRows) +
                rawPathCandidates.flatMap(::rawPathMetricRows) +
                retainedHistoryCandidates.flatMap(P2CandidateRetainedHistoryMeasurementReport::metricRows)
        Files.writeString(
            outputDirectory.resolve("host-candidates.csv"),
            rows.joinToString(System.lineSeparator(), postfix = System.lineSeparator()),
        )
    }

    private fun assertReportMatrix(
        candidates: List<P2CandidateMeasurementMetric>,
        patchCandidates: List<P2CandidatePatchMeasurementMetric>,
        rawPathCandidates: List<P2CandidateRawPathMeasurementMetric>,
        retainedHistoryCandidates: List<P2CandidateRetainedHistoryMeasurementMetric>,
    ) {
        val metricCount =
            candidates.size + patchCandidates.size + rawPathCandidates.size + retainedHistoryCandidates.size
        val sampleCount =
            candidates.sumOf { metric -> metric.samples.latenciesNanos.size } +
                patchCandidates.sumOf { metric -> metric.samples.latenciesNanos.size } +
                rawPathCandidates.sumOf { metric -> metric.samples.latenciesNanos.size } +
                retainedHistoryCandidates.sumOf { metric -> metric.samples.latenciesNanos.size }
        check(metricCount == EXPECTED_METRIC_COUNT) { "Candidate schema v6 metric count changed." }
        check(sampleCount == EXPECTED_RAW_SAMPLE_COUNT) { "Candidate schema v6 sample count changed." }
        check(retainedHistoryCandidates.size == P2CandidateRetainedHistoryMeasurement.METRIC_COUNT)
        check(
            retainedHistoryCandidates.sumOf { metric -> metric.samples.latenciesNanos.size } ==
                P2CandidateRetainedHistoryMeasurement.RAW_SAMPLE_COUNT,
        )
    }

    private fun metadataRows(): List<String> = baseMetadataRows() + contractMetadataRows()

    private fun baseMetadataRows(): List<String> =
        listOf(
            P2CandidateMeasurementReportSchema.headerRow(),
            metadataRow("schema", "nene-pixel-p2-representation-limits-host-candidates-v6"),
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
                    "inverse apply|round trip|late conflict plus raw duplicate changed|reference-clear changed|" +
                    "reference-clear no-op|same-color no-op plus 18 retained analytical-history workloads; " +
                    "5 warmups and 10 diagnostic samples",
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
                "candidate snapshot/apply/standalone-patch rows use path_positions=0; raw-path rows use the " +
                    "ordered raw count P and effective canonical change count C; those rows use history_entries=0; " +
                    "retained rows use path_positions=0, uniform per-entry C, entry count H, and total T",
            ),
            metadataRow(
                "patch_logical_storage_boundary",
                "change-record primitive fields, color-reference fields, occupied list-element reference slots, " +
                    "object records, and owned primitive arrays only; wrapper/list/array headers, spare list " +
                    "capacity, and shared PixelColor referents excluded; not retained bytes",
            ),
            metadataRow(
                "candidate_gaps",
                "production bounded history, retained heap, ART, PSS, frames, sparse/rectangular native patches, " +
                    "and semantic " +
                    "selection remain pending",
            ),
            metadataRow("cross_configuration_correctness", "all patch semantic and lifecycle digests matched"),
            metadataRow(
                "palette_status",
                "U8 value-palette pack/index correctness and 257-color typed rejection tested; " +
                    "performance comparison blocked on palette semantic ownership",
            ),
        ) + rawPathMetadataRows() + retainedHistoryMetadataRows()

    private fun rawPathMetadataRows(): List<String> =
        listOf(
            metadataRow(
                "raw_candidate_boundary",
                "test-only raw scan, first-occurrence duplicate collapse, source-color filter, canonical change " +
                    "collection, candidate-native patch materialization, and typed result; fixture generation, " +
                    "apply/inverse, history, and verification excluded",
            ),
            metadataRow(
                "raw_candidate_matrix",
                "256x256; opaque black/red analytical fixtures; paired-row-major duplicate changed, " +
                    "row-major reference-clear changed/no-op and same-color no-op; 5 configurations; " +
                    "20 metrics and 200 samples",
            ),
        )

    private fun retainedHistoryMetadataRows(): List<String> =
        listOf(
            metadataRow(
                "retained_history_boundary",
                "test-only analytical entry/history wrapper construction and defensive entry-reference ownership; " +
                    "prepared snapshot, patch, inverse, replay, digest, storage analysis, and verification excluded",
            ),
            metadataRow(
                "retained_history_matrix",
                "256x256 N=65536; H/T pairs 0/0,1/N,8|16|32|64 x N|2N|4N|8N; " +
                    "5 configurations; 90 metrics and 900 samples",
            ),
            metadataRow(
                "retained_history_storage_boundary",
                "snapshot, forward, inverse-additional, shared, and retained-union logical units remain separate; " +
                    "not retained heap, ART, Java live heap, PSS, or physical-device memory",
            ),
            metadataRow(
                "retained_history_cross_configuration_correctness",
                "entry-count and semantic digests matched across all five configurations per retained workload",
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

    private fun rawPathMetricRows(metric: P2CandidateRawPathMeasurementMetric): List<String> =
        listOf(rawPathRow(metric, P2CandidateReportSample("metric", null, null, null))) +
            metric.samples.latenciesNanos.indices.map { index ->
                rawPathRow(
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

    private fun rawPathRow(
        metric: P2CandidateRawPathMeasurementMetric,
        sample: P2CandidateReportSample,
    ): String {
        val descriptor = metric.descriptor
        val outcome = metric.outcome
        return rowByColumn(
            *rawPathBaseValues(metric, sample.recordType).toTypedArray(),
            *sample.rawPathValues(metric).toTypedArray(),
            *configurationValues(descriptor.configuration).toTypedArray(),
            "operation_kind" to descriptor.operation.csvName,
            "operation_boundary" to descriptor.protocol.boundary,
            "content_kind" to descriptor.contentKind,
            "path_kind" to "raw_single_color_path",
            "color_cardinality" to 1,
            "tile_edge" to descriptor.configuration.snapshotRepresentation.reportTileEdge(),
            "primitive_payload_bytes" to outcome.storage.snapshot.primitivePayloadBytes,
            "reference_slots" to outcome.storage.snapshot.referenceSlots,
            *patchStorageValues(outcome.storage.patch).toTypedArray(),
            *patchResultValues(outcome.result).toTypedArray(),
            *rawPathStateValues(outcome.state).toTypedArray(),
            "raw_input_digest_sha256" to outcome.correctness.rawInputDigest,
            "canonical_change_digest_sha256" to outcome.correctness.canonicalChangeDigest,
            "canonical_order_digest_sha256" to outcome.correctness.canonicalOrderDigest,
            "forward_patch_digest_sha256" to outcome.correctness.forwardPatchDigest,
            "inverse_patch_digest_sha256" to outcome.correctness.inversePatchDigest,
            "execution_order" to descriptor.protocol.executionOrder,
            "input_order" to descriptor.protocol.inputOrder,
            "correctness_status" to outcome.correctness.status,
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

    private fun rawPathStateValues(state: P2CandidateRawPathStateEvidence): List<Pair<String, Any>> =
        (state.affectedRegion?.let(::regionValues) ?: emptyList()) +
            lifecycleValues(state.lifecycle) +
            operationValues(state.operation) +
            listOf(
                "unaffected_pixel_count" to state.unaffectedPixelCount,
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

    private fun rawPathBaseValues(
        metric: P2CandidateRawPathMeasurementMetric,
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
            "path_positions" to descriptor.pathPositions,
            "unique_path_positions" to descriptor.uniquePathPositions,
            "duplicate_path_positions" to descriptor.duplicatePathPositions,
            "unchanged_unique_positions" to descriptor.unchangedUniquePositions,
            "change_count" to descriptor.changeCount,
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

    private fun P2CandidateReportSample.rawPathValues(
        metric: P2CandidateRawPathMeasurementMetric,
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

    private fun rowByColumn(vararg values: Pair<String, Any>): String =
        P2CandidateMeasurementReportSchema.rowByColumn(*values)

    private fun systemDescription(): String =
        listOf("os.name", "os.version", "os.arch").joinToString(" ", transform = ::requiredSystemProperty)

    private fun jvmDescription(): String =
        listOf("java.vm.name", "java.runtime.version").joinToString(" ", transform = ::requiredSystemProperty)

    private fun requiredSystemProperty(name: String): String =
        requireNotNull(System.getProperty(name)) { "Required JVM system property '$name' is unavailable." }

    private const val OUTPUT_DIRECTORY_PROPERTY: String = "nene.p2.representation.measurement.outputDirectory"
    private const val HOST_PROFILE: String = "NENE-P2-REPRESENTATION-WINDOWS-I9-10850K-JBR21"
    private const val CANDIDATE_WARMUPS: Int = 5
    private const val CANDIDATE_SAMPLES: Int = 10
    private const val EXPECTED_METRIC_COUNT: Int = 490
    private const val EXPECTED_RAW_SAMPLE_COUNT: Int = 4_900
}

private data class P2CandidateReportSample(
    val recordType: String,
    val index: Int?,
    val latencyNanos: Long?,
    val allocatedBytes: Long?,
)
