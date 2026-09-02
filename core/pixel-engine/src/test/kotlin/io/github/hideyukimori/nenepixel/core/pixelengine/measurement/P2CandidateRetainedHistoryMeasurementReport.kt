package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

internal object P2CandidateRetainedHistoryMeasurementReport {
    fun metricRows(metric: P2CandidateRetainedHistoryMeasurementMetric): List<String> =
        listOf(row(metric, P2RetainedHistoryReportSample("metric", null, null, null))) +
            metric.samples.latenciesNanos.indices.map { index ->
                row(
                    metric,
                    P2RetainedHistoryReportSample(
                        recordType = "sample",
                        index = index,
                        latencyNanos = metric.samples.latenciesNanos[index],
                        allocatedBytes = metric.samples.allocatedBytes[index],
                    ),
                )
            }

    private fun row(
        metric: P2CandidateRetainedHistoryMeasurementMetric,
        sample: P2RetainedHistoryReportSample,
    ): String {
        val descriptor = metric.descriptor
        val outcome = metric.outcome
        return P2CandidateMeasurementReportSchema.rowByColumn(
            *baseValues(metric, sample.recordType).toTypedArray(),
            *sample.values(metric).toTypedArray(),
            *configurationValues(descriptor.configuration).toTypedArray(),
            "operation_kind" to "retained_analytical_history",
            "operation_boundary" to descriptor.boundary,
            "content_kind" to "uniform_opaque_black_red_green_analytical",
            "path_kind" to "retained_row_major_prefix",
            "color_cardinality" to colorCardinality(descriptor.workload.historyEntries),
            "tile_edge" to descriptor.configuration.snapshotRepresentation.reportTileEdge(),
            *storageValues(outcome.storage).toTypedArray(),
            "result_kind" to "Retained",
            "retained_entry_change_counts_digest_sha256" to outcome.correctness.entryChangeCountsDigest,
            "retained_history_semantic_digest_sha256" to outcome.correctness.semanticDigest,
            "execution_order" to descriptor.executionOrder,
            "input_order" to "history_ascending_revision_row_major_prefix",
            "correctness_status" to outcome.correctness.status,
        )
    }

    private fun baseValues(
        metric: P2CandidateRetainedHistoryMeasurementMetric,
        recordType: String,
    ): List<Pair<String, Any>> {
        val descriptor = metric.descriptor
        val workload = descriptor.workload
        return listOf(
            "record_type" to recordType,
            "name" to "p2_candidate_retained_analytical_history",
            "status" to "measured_test_only",
            "canvas_width" to descriptor.canvas.width,
            "canvas_height" to descriptor.canvas.height,
            "pixel_count" to descriptor.canvas.pixelCount,
            "path_positions" to 0,
            "change_count" to workload.changeCountPerEntry,
            "history_entries" to workload.historyEntries,
            "total_retained_changes" to workload.totalRetainedChanges,
            "warmup" to P2CandidateRetainedHistoryMeasurement.WARMUP_ITERATIONS,
            "samples" to P2CandidateRetainedHistoryMeasurement.SAMPLE_COUNT,
            "boundary" to descriptor.boundary,
        )
    }

    private fun configurationValues(configuration: P2CandidateConfiguration): List<Pair<String, Any>> =
        listOf(
            "configuration_id" to configuration.configurationId,
            "snapshot_candidate_id" to configuration.snapshotRepresentation.candidateId,
            "patch_candidate_id" to configuration.patchLayout.candidateId,
            "inverse_policy" to configuration.patchLayout.inversePolicy.csvName,
        )

    private fun storageValues(storage: P2CandidateRetainedHistoryStorageEvidence): List<Pair<String, Any>> =
        listOf(
            "retained_snapshot_primitive_bytes" to storage.snapshot.primitivePayloadBytes,
            "retained_snapshot_reference_slots" to storage.snapshot.referenceSlots,
        ) +
            patchStorageValues("retained_forward_patch", storage.forward) +
            patchStorageValues("retained_inverse_additional", storage.inverseAdditional) +
            patchStorageValues("retained_shared_patch", storage.shared) +
            patchStorageValues("retained_history_patch_union", storage.retainedUnion)

    private fun patchStorageValues(
        prefix: String,
        storage: P2CandidatePatchStorageCounts,
    ): List<Pair<String, Any>> =
        listOf(
            "${prefix}_primitive_bytes" to storage.primitivePayloadBytes,
            "${prefix}_reference_slots" to storage.referenceSlots,
            "${prefix}_object_records" to storage.objectRecords,
            "${prefix}_primitive_backing_arrays" to storage.primitiveBackingArrays,
        )

    private fun P2RetainedHistoryReportSample.values(
        metric: P2CandidateRetainedHistoryMeasurementMetric,
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

    private fun colorCardinality(historyEntries: Int): Int =
        when (historyEntries) {
            0 -> 1
            1 -> 2
            else -> 3
        }

    private fun P2CandidateRepresentation.reportTileEdge(): Int =
        when (this) {
            P2CandidateRepresentation.TiledCowRgba8888T16 -> 16
            P2CandidateRepresentation.TiledCowRgba8888T32 -> 32
            P2CandidateRepresentation.TiledCowRgba8888T64 -> 64
            else -> 0
        }
}

private data class P2RetainedHistoryReportSample(
    val recordType: String,
    val index: Int?,
    val latencyNanos: Long?,
    val allocatedBytes: Long?,
)
