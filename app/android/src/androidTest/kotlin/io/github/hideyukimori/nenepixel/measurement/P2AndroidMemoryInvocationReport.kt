package io.github.hideyukimori.nenepixel.measurement

import android.app.ActivityManager
import android.os.Build
import java.io.File

internal data class P2AndroidMemoryCheckpointPair(
    val baseline: PostGcMemorySnapshot,
    val retained: PostGcMemorySnapshot,
)

internal data class P2AndroidMemoryInvocationObservations(
    val physicalCheckpoints: List<P2AndroidPhysicalCheckpoint>,
    val memory: P2AndroidMemoryCheckpointPair,
    val prepared: P2AndroidMemoryPreparedRetainedState,
)

internal data class P2AndroidMemoryInvocationInput(
    val environment: P2AndroidMeasurementEnvironment,
    val identity: P2AndroidMemoryRunIdentity,
    val observations: P2AndroidMemoryInvocationObservations,
)

internal object P2AndroidMemoryInvocationReport {
    fun write(input: P2AndroidMemoryInvocationInput): File {
        validate(input)
        val rows =
            metadataRows(input) +
                physicalRow(input, input.observations.physicalCheckpoints[0]) +
                memoryRow(input, "baseline_post_gc", 0, input.observations.memory.baseline) +
                entryRows(input) +
                memoryRow(input, "retained_post_gc", 1, input.observations.memory.retained) +
                physicalRow(input, input.observations.physicalCheckpoints[1]) +
                summaryRow(input)
        val output = input.environment.memoryRunOutputFile(input.identity.run.runIndex)
        return P2AndroidMemoryCsv.writeImmutable(output, rows)
    }

    private fun metadataRows(input: P2AndroidMemoryInvocationInput): List<P2AndroidMemoryCsvRow> {
        val environment = input.environment
        val identity = input.identity
        val activityManager = environment.targetContext.getSystemService(ActivityManager::class.java)
        return listOf(
            metadata(input, "schema", P2AndroidMemoryProtocol.INVOCATION_SCHEMA),
            metadata(input, "output_identity", "device-memory-run"),
            metadata(input, "run_status", "valid"),
            metadata(input, "app_variant", "debug"),
            metadata(input, "test_variant", "debugAndroidTest"),
            metadata(input, "manufacturer", Build.MANUFACTURER),
            metadata(input, "model", Build.MODEL),
            metadata(input, "product", Build.PRODUCT),
            metadata(input, "hardware", Build.HARDWARE),
            metadata(input, "api_level", Build.VERSION.SDK_INT),
            metadata(input, "build_fingerprint", Build.FINGERPRINT),
            metadata(input, "security_patch", Build.VERSION.SECURITY_PATCH),
            metadata(input, "supported_abis", Build.SUPPORTED_ABIS.joinToString("|")),
            metadata(input, "runtime_max_memory_bytes", Runtime.getRuntime().maxMemory()),
            metadata(input, "memory_class_mib", activityManager.memoryClass),
            metadata(input, "canvas", P2AndroidMemoryProtocol.CANVAS_DESCRIPTOR),
            metadata(input, "retained_workload", P2AndroidMemoryProtocol.RETAINED_WORKLOAD_DESCRIPTOR),
            metadata(input, "entry_sequence", P2AndroidMemoryProtocol.ENTRY_SEQUENCE_DESCRIPTOR),
            metadata(input, "gc_protocol", P2AndroidMemoryProtocol.GC_PROTOCOL_DESCRIPTOR),
            metadata(input, "capture_sequence", P2AndroidMemoryProtocol.CAPTURE_SEQUENCE_DESCRIPTOR),
            metadata(input, "retained_boundary", P2AndroidMemoryProtocol.RETAINED_OWNER_DESCRIPTOR),
            metadata(input, "projection_boundary", P2AndroidMemoryProtocol.PROJECTION_BOUNDARY_DESCRIPTOR),
            metadata(input, "private_patch_boundary", P2AndroidMemoryProtocol.PRIVATE_PATCH_BOUNDARY_DESCRIPTOR),
            metadata(input, "post_gc_churn_status", P2AndroidMemoryProtocol.CHURN_STATUS),
            metadata(input, "peak_headroom_status", UNEVALUATED),
            metadata(input, "candidate_retained_memory_status", UNEVALUATED),
            metadata(input, "candidate_projection_status", UNEVALUATED),
        )
    }

    private fun metadata(
        input: P2AndroidMemoryInvocationInput,
        name: String,
        value: Any,
    ): P2AndroidMemoryCsvRow =
        P2AndroidMemoryCsv.row(
            "record_type" to "metadata",
            "name" to name,
            "value" to value,
            *identityValues(input).toTypedArray(),
        )

    private fun physicalRow(
        input: P2AndroidMemoryInvocationInput,
        checkpoint: P2AndroidPhysicalCheckpoint,
    ): P2AndroidMemoryCsvRow =
        P2AndroidMemoryCsv.row(
            "record_type" to "physical_checkpoint",
            "name" to checkpoint.name,
            *identityValues(input).toTypedArray(),
            "display_mode_id" to checkpoint.displayModeId,
            "display_width_pixels" to checkpoint.physicalWidthPixels,
            "display_height_pixels" to checkpoint.physicalHeightPixels,
            "refresh_rate_hertz" to checkpoint.refreshRateHertz,
            "thermal_status" to checkpoint.thermalStatus,
            "power_save_mode" to checkpoint.powerSaveMode,
            "interactive" to checkpoint.interactive,
            "usb_powered" to checkpoint.usbPowered,
            "battery_level_percent" to checkpoint.batteryLevelPercent,
            "correctness_status" to PASS,
        )

    private fun memoryRow(
        input: P2AndroidMemoryInvocationInput,
        name: String,
        checkpointIndex: Int,
        memory: PostGcMemorySnapshot,
    ): P2AndroidMemoryCsvRow {
        val activityManager = input.environment.targetContext.getSystemService(ActivityManager::class.java)
        return P2AndroidMemoryCsv.row(
            "record_type" to "memory_checkpoint",
            "name" to name,
            *identityValues(input).toTypedArray(),
            *workloadValues().toTypedArray(),
            "memory_checkpoint_index" to checkpointIndex,
            "post_gc_java_heap_used_bytes" to memory.javaHeapUsedBytes,
            "post_gc_java_heap_committed_bytes" to memory.javaHeapCommittedBytes,
            "runtime_max_memory_bytes" to Runtime.getRuntime().maxMemory(),
            "memory_class_mib" to activityManager.memoryClass,
            "total_pss_kb" to memory.totalPssKilobytes,
            "dalvik_pss_kb" to memory.dalvikPssKilobytes,
            "native_pss_kb" to memory.nativePssKilobytes,
            "other_pss_kb" to memory.otherPssKilobytes,
            "total_private_dirty_kb" to memory.totalPrivateDirtyKilobytes,
            "total_shared_dirty_kb" to memory.totalSharedDirtyKilobytes,
            "correctness_status" to PASS,
            "boundary" to
                if (checkpointIndex == 0) {
                    P2AndroidMemoryProtocol.BASELINE_BOUNDARY
                } else {
                    P2AndroidMemoryProtocol.RETAINED_BOUNDARY
                },
        )
    }

    private fun entryRows(input: P2AndroidMemoryInvocationInput): List<P2AndroidMemoryCsvRow> =
        input.observations.prepared.owner.changeSets.mapIndexed { index, changeSet ->
            val expectation = P2AndroidMemoryValues.entryExpectation(index)
            val region = changeSet.renderInvalidation
            P2AndroidMemoryCsv.row(
                "record_type" to "retained_entry",
                "name" to "entry_${index.toString().padStart(2, '0')}",
                *identityValues(input).toTypedArray(),
                *workloadValues().toTypedArray(),
                "entry_index" to index,
                "block_index" to expectation.blockIndex,
                "target_argb_hex" to argbHex(expectation.targetArgb),
                "before_revision" to changeSet.beforeRevision.value,
                "after_revision" to changeSet.afterRevision.value,
                "invalidation_origin_x" to region.origin.x.value,
                "invalidation_origin_y" to region.origin.y.value,
                "invalidation_width" to region.size.width.value,
                "invalidation_height" to region.size.height.value,
                "correctness_status" to PASS,
            )
        }

    private fun summaryRow(input: P2AndroidMemoryInvocationInput): P2AndroidMemoryCsvRow {
        val prepared = input.observations.prepared
        val owner = prepared.owner
        val projection = owner.projection
        return P2AndroidMemoryCsv.row(
            "record_type" to "retained_summary",
            "name" to P2AndroidMemoryProtocol.WORKLOAD_ID,
            *identityValues(input).toTypedArray(),
            *workloadValues().toTypedArray(),
            "final_revision" to owner.finalDocumentState.revision.value,
            "history_after" to "undo_available",
            "document_hash" to owner.finalDocumentState.hashCode(),
            "snapshot_hash" to owner.finalDocumentState.snapshot.hashCode(),
            "final_pixel_digest_sha256" to prepared.correctness.finalPixelDigestSha256,
            "entry_descriptor_digest_sha256" to prepared.correctness.entryDescriptorDigestSha256,
            "projection_pixel_count" to projection.pixelCount,
            "projection_first_x" to projection.firstX,
            "projection_first_y" to projection.firstY,
            "projection_first_argb_hex" to argbHex(projection.firstArgb),
            "projection_last_x" to projection.lastX,
            "projection_last_y" to projection.lastY,
            "projection_last_argb_hex" to argbHex(projection.lastArgb),
            "projection_digest_sha256" to projection.projectionDigestSha256,
            "projection_mismatch_count" to projection.mismatchCount(P2AndroidMemoryProtocol.OPAQUE_WHITE_ARGB),
            "post_gc_churn_status" to P2AndroidMemoryProtocol.CHURN_STATUS,
            "peak_headroom_status" to UNEVALUATED,
            "candidate_retained_memory_status" to UNEVALUATED,
            "candidate_projection_status" to UNEVALUATED,
            "correctness_status" to PASS,
            "boundary" to P2AndroidMemoryProtocol.SUMMARY_BOUNDARY,
        )
    }

    private fun identityValues(input: P2AndroidMemoryInvocationInput): List<Pair<String, Any?>> {
        val identity = input.identity
        return listOf(
            "schema" to P2AndroidMemoryProtocol.INVOCATION_SCHEMA,
            "evidence_class" to input.environment.evidenceClass,
            "physical_profile_id" to input.environment.profileId,
            "candidate_id" to identity.run.candidateId,
            "workload_id" to P2AndroidMemoryProtocol.WORKLOAD_ID,
            "batch_id" to identity.batchId,
            "run_index" to identity.run.runIndex,
            "source_commit" to identity.run.sourceCommit,
            "process_id" to identity.processId,
            "process_start_elapsed_realtime_ms" to identity.processStartElapsedRealtimeMillis,
        )
    }

    private fun workloadValues(): List<Pair<String, Any>> =
        listOf(
            "canvas_width" to P2AndroidMemoryProtocol.CANVAS_EDGE,
            "canvas_height" to P2AndroidMemoryProtocol.CANVAS_EDGE,
            "pixel_count" to P2AndroidMemoryProtocol.PIXEL_COUNT,
            "history_entries" to P2AndroidMemoryProtocol.HISTORY_ENTRIES,
            "change_count_per_entry" to P2AndroidMemoryProtocol.CHANGE_COUNT_PER_ENTRY,
            "total_retained_changes" to P2AndroidMemoryProtocol.TOTAL_RETAINED_CHANGES,
        )

    private fun validate(input: P2AndroidMemoryInvocationInput) {
        P2AndroidMemoryProtocol.validateEnvironment(input.environment)
        val checkpoints = input.observations.physicalCheckpoints
        check(checkpoints.map(P2AndroidPhysicalCheckpoint::name) == listOf("before_baseline", "after_retained"))
        checkpoints.first().assertInitialValidity()
        checkpoints.last().assertCompatibleWith(checkpoints.first())
        validateMemory(input.observations.memory.baseline)
        validateMemory(input.observations.memory.retained)
        val prepared = input.observations.prepared
        check(prepared.owner.changeSets.size == P2AndroidMemoryProtocol.HISTORY_ENTRIES)
        check(prepared.owner.finalDocumentState.revision.value == P2AndroidMemoryProtocol.FINAL_REVISION)
        check(prepared.correctness.finalPixelDigestSha256 == P2AndroidMemoryProtocol.EXPECTED_PROJECTION_DIGEST)
        check(
            prepared.correctness.entryDescriptorDigestSha256 ==
                P2AndroidMemoryProtocol.EXPECTED_ENTRY_DESCRIPTOR_DIGEST,
        )
        check(prepared.owner.projection.projectionDigestSha256 == P2AndroidMemoryProtocol.EXPECTED_PROJECTION_DIGEST)
        check(prepared.owner.projection.mismatchCount(P2AndroidMemoryProtocol.OPAQUE_WHITE_ARGB) == 0)
    }

    private fun validateMemory(memory: PostGcMemorySnapshot) {
        check(memory.javaHeapUsedBytes > 0L)
        check(memory.javaHeapCommittedBytes >= memory.javaHeapUsedBytes)
        check(memory.totalPssKilobytes > 0)
        check(memory.dalvikPssKilobytes >= 0 && memory.nativePssKilobytes >= 0 && memory.otherPssKilobytes >= 0)
        check(memory.totalPrivateDirtyKilobytes >= 0 && memory.totalSharedDirtyKilobytes >= 0)
    }

    private fun argbHex(argb: Int): String =
        argb
            .toUInt()
            .toString(HEX_RADIX)
            .uppercase()
            .padStart(ARGB_HEX_LENGTH, '0')

    private const val PASS: String = "pass"
    private const val UNEVALUATED: String = "not_evaluated"
    private const val HEX_RADIX: Int = 16
    private const val ARGB_HEX_LENGTH: Int = 8
}
