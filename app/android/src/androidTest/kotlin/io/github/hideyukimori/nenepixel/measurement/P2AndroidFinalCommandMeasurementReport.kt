package io.github.hideyukimori.nenepixel.measurement

import android.app.ActivityManager
import android.os.Build
import java.io.File

internal object P2AndroidFinalCommandMeasurementReport {
    fun write(input: P2AndroidFinalCommandReportInput): File {
        validate(input)
        val output = input.environment.finalCommandOutputFile(input.plan)
        val outputDirectory = requireNotNull(output.parentFile)
        check(outputDirectory.isDirectory || outputDirectory.mkdirs()) {
            "Failed to create final command measurement output directory."
        }
        return P2AndroidFinalCommandOutputPublication.publish(
            output = output,
            policy = input.plan.publicationPolicy,
            writeRows = { target -> writeRows(target, input) },
        )
    }

    private fun writeRows(
        output: File,
        input: P2AndroidFinalCommandReportInput,
    ) {
        output.bufferedWriter().use { writer ->
            writer.appendLine(csvRow(*COLUMNS.toTypedArray()))
            metadataRows(input).forEach(writer::appendLine)
            sortedArtRuntimeStats().forEach { (name, value) ->
                writer.appendLine(metadataRow("runtime_stat_end:$name", value))
            }
            writer.appendLine(baselineRow(input))
            input.checkpoints.forEach { checkpoint -> writer.appendLine(checkpointRow(input, checkpoint)) }
            input.samples.forEach { sample -> writer.appendLine(sampleRow(input, sample)) }
        }
    }

    internal fun contractMetadataRows(
        plan: P2AndroidFinalCommandPlan,
        identity: P2AndroidRunIdentity,
    ): Map<String, String> {
        val values =
            linkedMapOf(
                "schema" to plan.schema,
                "output_identity" to plan.outputIdentity,
                "candidate_id" to identity.candidateId,
                "run_index" to identity.runIndex.toString(),
                "canvas" to plan.canvasMetadata,
                "workload_order" to plan.workloadNames.joinToString("|"),
                "warmup_iterations_per_workload" to plan.warmupIterations.toString(),
                "sample_count_per_workload" to plan.samplesPerWorkload.toString(),
                "sample_count_total" to plan.totalSampleCount.toString(),
                "sample_indices" to
                    "local=1..${plan.samplesPerWorkload};global=1..${plan.totalSampleCount}",
                "checkpoint_interval_global_samples" to
                    P2AndroidPhysicalCheckpointPolicy.CHECKPOINT_INTERVAL.toString(),
                "checkpoint_row_count" to plan.checkpointCount.toString(),
            )
        return values.mapValues { (name, value) -> metadataRow(name, value) }
    }

    private fun metadataRows(input: P2AndroidFinalCommandReportInput): List<String> {
        val environment = input.environment
        val identity = input.identity
        val activityManager = environment.targetContext.getSystemService(ActivityManager::class.java)
        val contractRows = contractMetadataRows(input.plan, identity)
        return listOf(
            contractRows.getValue("schema"),
            contractRows.getValue("output_identity"),
            metadataRow("run_status", "valid"),
            contractRows.getValue("candidate_id"),
            contractRows.getValue("run_index"),
            metadataRow("source_commit", identity.sourceCommit),
            metadataRow("app_variant", "debug"),
            metadataRow("test_variant", "debugAndroidTest"),
            metadataRow("evidence_class", environment.evidenceClass),
            metadataRow("physical_profile_id", environment.profileId),
            metadataRow("manufacturer", Build.MANUFACTURER),
            metadataRow("model", Build.MODEL),
            metadataRow("product", Build.PRODUCT),
            metadataRow("hardware", Build.HARDWARE),
            metadataRow("api_level", Build.VERSION.SDK_INT.toString()),
            metadataRow("build_fingerprint", Build.FINGERPRINT),
            metadataRow("security_patch", Build.VERSION.SECURITY_PATCH),
            metadataRow("supported_abis", Build.SUPPORTED_ABIS.joinToString("|")),
            metadataRow("runtime_max_memory_bytes", Runtime.getRuntime().maxMemory().toString()),
            metadataRow("memory_class_mib", activityManager.memoryClass.toString()),
            metadataRow("ro.kernel.qemu", environment.emulatorDetection.kernelQemu),
            metadataRow("ro.boot.qemu", environment.emulatorDetection.bootQemu),
            metadataRow(
                "emulator_signals",
                environment.emulatorDetection.signals
                    .ifEmpty { listOf("none") }
                    .joinToString("|"),
            ),
            contractRows.getValue("canvas"),
            contractRows.getValue("workload_order"),
            contractRows.getValue("warmup_iterations_per_workload"),
            contractRows.getValue("sample_count_per_workload"),
            contractRows.getValue("sample_count_total"),
            contractRows.getValue("sample_indices"),
            contractRows.getValue("checkpoint_interval_global_samples"),
            contractRows.getValue("checkpoint_row_count"),
            metadataRow(
                "measurement_boundary",
                "one prepared CommandGateway.execute call only; fixture, ART snapshots, post-GC heap/PSS, " +
                    "correctness, checkpoints, and report writing excluded from direct latency",
            ),
            metadataRow(
                "art_runtime_boundary",
                "approximate process ART runtime-stat delta between Debug snapshots around the one execute call; " +
                    "Debug snapshot overhead is included in the ART delta but excluded from direct latency",
            ),
            metadataRow(
                "memory_boundary",
                "one post-GC baseline before measured samples; per-sample diagnostic post-verification Java heap and " +
                    "process PSS while retaining the prepared gateway, document, and one-level history",
            ),
            metadataRow(
                "correctness_boundary",
                "outside timing: exact DocumentState and complete pixels, revision, history, result kind, public " +
                    "ChangeSet revisions and render invalidation, or exact no-op rejection and unchanged state identity",
            ),
            metadataRow(
                "validity_boundary",
                "physical profile only; stable display mode and dimensions; 90 Hz; thermal status <= 1; power-save " +
                    "disabled; interactive display; USB power; valid battery data",
            ),
            metadataRow(
                "lower_level_boundary",
                "private patch ordering, inverse records, and unaffected-pixel proofs remain in isolated canonical core tests",
            ),
        )
    }

    private fun baselineRow(input: P2AndroidFinalCommandReportInput): String =
        rowByColumn(
            "record_type" to "baseline",
            "name" to "process_post_gc_before_samples",
            *identityValues(input).toTypedArray(),
            *memoryValues(input.baseline).toTypedArray(),
            "boundary" to "post-GC process baseline captured after all warmups and before measured samples",
        )

    private fun checkpointRow(
        input: P2AndroidFinalCommandReportInput,
        checkpoint: P2AndroidPhysicalCheckpoint,
    ): String =
        rowByColumn(
            "record_type" to "checkpoint",
            "name" to checkpoint.name,
            *identityValues(input).toTypedArray(),
            "global_sample_index" to checkpoint.sampleIndex,
            *checkpoint.reportValues().toTypedArray(),
        )

    private fun sampleRow(
        input: P2AndroidFinalCommandReportInput,
        sample: P2AndroidFinalCommandSample,
    ): String {
        val outcome = sample.outcome
        val invalidation = outcome.renderInvalidation
        return rowByColumn(
            "record_type" to "sample",
            "name" to sample.spec.kind.metricName,
            *identityValues(input).toTypedArray(),
            "canvas_width" to sample.spec.canvasEdge,
            "canvas_height" to sample.spec.canvasEdge,
            "position_count" to sample.spec.positionCount,
            "warmup_iterations" to input.plan.warmupIterations,
            "sample_count" to input.plan.samplesPerWorkload,
            "local_sample_index" to sample.localSampleIndex,
            "global_sample_index" to sample.globalSampleIndex,
            "latency_nanos" to sample.latencyNanos,
            "result_kind" to outcome.resultKind,
            "revision_after" to outcome.revision,
            "history_after" to outcome.history,
            "document_hash" to outcome.documentHash,
            "snapshot_hash" to outcome.snapshotHash,
            "change_set_before_revision" to outcome.changeSetBeforeRevision,
            "change_set_after_revision" to outcome.changeSetAfterRevision,
            "render_invalidation_origin_x" to invalidation?.originX,
            "render_invalidation_origin_y" to invalidation?.originY,
            "render_invalidation_width" to invalidation?.width,
            "render_invalidation_height" to invalidation?.height,
            "unchanged_state_identity" to outcome.unchangedStateIdentity,
            *runtimeValues(sample.runtimeDelta).toTypedArray(),
            *memoryValues(sample.memory).toTypedArray(),
            "boundary" to SAMPLE_ASSERTION_BOUNDARY,
        )
    }

    private fun identityValues(input: P2AndroidFinalCommandReportInput): List<Pair<String, Any?>> =
        listOf(
            "evidence_class" to input.environment.evidenceClass,
            "physical_profile_id" to input.environment.profileId,
            "candidate_id" to input.identity.candidateId,
            "run_index" to input.identity.runIndex,
            "source_commit" to input.identity.sourceCommit,
        )

    private fun runtimeValues(delta: ArtRuntimeDelta): List<Pair<String, Any?>> =
        listOf(
            "art_allocated_bytes_before" to delta.allocatedBytesBefore,
            "art_allocated_bytes_after" to delta.allocatedBytesAfter,
            "art_allocated_bytes_delta" to delta.allocatedBytesDelta,
            "art_gc_count_delta" to delta.gcCountDelta,
            "art_gc_time_ms_delta" to delta.gcTimeMillisDelta,
            "art_blocking_gc_count_delta" to delta.blockingGcCountDelta,
            "art_blocking_gc_time_ms_delta" to delta.blockingGcTimeMillisDelta,
        )

    private fun memoryValues(memory: PostGcMemorySnapshot): List<Pair<String, Any?>> =
        listOf(
            "post_gc_java_heap_used_bytes" to memory.javaHeapUsedBytes,
            "post_gc_java_heap_committed_bytes" to memory.javaHeapCommittedBytes,
            "total_pss_kb" to memory.totalPssKilobytes,
            "dalvik_pss_kb" to memory.dalvikPssKilobytes,
            "native_pss_kb" to memory.nativePssKilobytes,
            "other_pss_kb" to memory.otherPssKilobytes,
            "total_private_dirty_kb" to memory.totalPrivateDirtyKilobytes,
            "total_shared_dirty_kb" to memory.totalSharedDirtyKilobytes,
        )

    private fun validate(input: P2AndroidFinalCommandReportInput) {
        P2AndroidFinalCommandProtocol.validate(input.environment, input.identity, input.plan)
        P2AndroidFinalCommandContractValidator.validateMemory(input.baseline)
        P2AndroidFinalCommandContractValidator.validateSamples(input.plan, input.samples)
        P2AndroidFinalCommandContractValidator.validateCheckpoints(input.plan, input.checkpoints)
    }

    private fun metadataRow(
        name: String,
        value: String,
    ): String = rowByColumn("record_type" to "metadata", "name" to name, "value" to value)

    private fun rowByColumn(vararg values: Pair<String, Any?>): String {
        val valuesByColumn = values.toMap()
        check(valuesByColumn.keys.all(COLUMNS::contains)) { "Unknown final command report column." }
        return csvRow(*COLUMNS.map { column -> valuesByColumn[column] ?: "" }.toTypedArray())
    }

    private fun csvRow(vararg values: Any): String =
        values.joinToString(",") { value -> "\"${value.toString().replace("\"", "\"\"")}\"" }

    private const val SAMPLE_ASSERTION_BOUNDARY: String =
        "exact state and complete pixels, revision, history, result, public ChangeSet revisions and invalidation, " +
            "or exact no-op and unchanged identity asserted outside latency"
    private val COLUMNS: List<String> =
        listOf(
            "record_type",
            "name",
            "value",
            "evidence_class",
            "physical_profile_id",
            "candidate_id",
            "run_index",
            "source_commit",
            "canvas_width",
            "canvas_height",
            "position_count",
            "warmup_iterations",
            "sample_count",
            "local_sample_index",
            "global_sample_index",
            "latency_nanos",
            "result_kind",
            "revision_after",
            "history_after",
            "document_hash",
            "snapshot_hash",
            "change_set_before_revision",
            "change_set_after_revision",
            "render_invalidation_origin_x",
            "render_invalidation_origin_y",
            "render_invalidation_width",
            "render_invalidation_height",
            "unchanged_state_identity",
            "art_allocated_bytes_before",
            "art_allocated_bytes_after",
            "art_allocated_bytes_delta",
            "art_gc_count_delta",
            "art_gc_time_ms_delta",
            "art_blocking_gc_count_delta",
            "art_blocking_gc_time_ms_delta",
            "post_gc_java_heap_used_bytes",
            "post_gc_java_heap_committed_bytes",
            "total_pss_kb",
            "dalvik_pss_kb",
            "native_pss_kb",
            "other_pss_kb",
            "total_private_dirty_kb",
            "total_shared_dirty_kb",
            "display_mode_id",
            "display_width_pixels",
            "display_height_pixels",
            "refresh_rate_hertz",
            "thermal_status",
            "power_save_mode",
            "interactive",
            "usb_powered",
            "battery_level_percent",
            "boundary",
        )
}
