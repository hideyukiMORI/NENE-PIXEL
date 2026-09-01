package io.github.hideyukimori.nenepixel.measurement

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

internal data class P2AndroidFrameMeasurementSample(
    val sampleIndex: Int,
    val generation: Long,
    val commandStartNanos: Long,
    val commandFinishNanos: Long,
    val renderStatePublishedNanos: Long,
    val frameCompletionNanos: Long,
    val commandToFirstCorrectFrameNanos: Long,
    val expectedRevision: Long,
    val expectedSnapshotHash: Int,
    val correlatedFrame: P2CorrelatedFrame,
    val correctness: P2FrameImageCorrectness,
)

internal data class P2FrameImageCorrectness(
    val imageWidth: Int,
    val imageHeight: Int,
    val checkedLogicalPixels: Int,
    val mismatchCount: Int,
    val sampledArgbHash: Int,
)

internal data class P2FrameDeviceCheckpoint(
    val name: String,
    val sampleIndex: Int,
    val displayModeId: Int,
    val physicalWidthPixels: Int,
    val physicalHeightPixels: Int,
    val refreshRateHertz: Float,
    val thermalStatus: Int,
    val powerSaveMode: Boolean,
    val interactive: Boolean,
    val usbPowered: Boolean,
    val batteryLevelPercent: Int,
)

internal data class P2FrameRunIdentity(
    val candidateId: String,
    val runIndex: Int,
    val sourceCommit: String,
) {
    internal companion object {
        fun fromRunnerArguments(): P2FrameRunIdentity {
            val arguments = InstrumentationRegistry.getArguments()
            val candidateId =
                requireNotNull(arguments.getString(CANDIDATE_ID_ARGUMENT)?.trim()?.takeIf(String::isNotEmpty)) {
                    "Runner argument '$CANDIDATE_ID_ARGUMENT' is required for frame measurements."
                }
            val runIndex =
                requireNotNull(arguments.getString(RUN_INDEX_ARGUMENT)?.toIntOrNull()?.takeIf { it > 0 }) {
                    "Runner argument '$RUN_INDEX_ARGUMENT' must be a positive integer."
                }
            val sourceCommit =
                requireNotNull(
                    arguments
                        .getString(SOURCE_COMMIT_ARGUMENT)
                        ?.trim()
                        ?.lowercase()
                        ?.takeIf(SOURCE_COMMIT_PATTERN::matches),
                ) {
                    "Runner argument '$SOURCE_COMMIT_ARGUMENT' must be a full 40-character Git commit."
                }
            return P2FrameRunIdentity(candidateId, runIndex, sourceCommit)
        }

        const val CANDIDATE_ID_ARGUMENT: String = "nene.p2.candidateId"
        const val RUN_INDEX_ARGUMENT: String = "nene.p2.runIndex"
        const val SOURCE_COMMIT_ARGUMENT: String = "nene.p2.sourceCommit"
        private val SOURCE_COMMIT_PATTERN: Regex = Regex("[0-9a-f]{40}")
    }
}

internal object P2AndroidFrameMeasurementReport {
    fun write(
        environment: P2AndroidMeasurementEnvironment,
        identity: P2FrameRunIdentity,
        checkpoints: List<P2FrameDeviceCheckpoint>,
        samples: List<P2AndroidFrameMeasurementSample>,
    ): File {
        val output = environment.frameOutputFile
        val outputDirectory = requireNotNull(output.parentFile)
        check(outputDirectory.isDirectory || outputDirectory.mkdirs()) {
            "Failed to create frame measurement output directory."
        }
        output.bufferedWriter().use { writer ->
            writer.appendLine(csvRow(*COLUMNS.toTypedArray()))
            metadataRows(environment, identity).forEach(writer::appendLine)
            checkpoints.forEach { checkpoint -> writer.appendLine(checkpointRow(environment, identity, checkpoint)) }
            samples.forEach { sample -> writer.appendLine(sampleRow(environment, identity, sample)) }
        }
        return output
    }

    private fun metadataRows(
        environment: P2AndroidMeasurementEnvironment,
        identity: P2FrameRunIdentity,
    ): List<String> =
        listOf(
            metadataRow("schema", SCHEMA),
            metadataRow("output_identity", "device-frames"),
            metadataRow("run_status", "valid"),
            metadataRow("candidate_id", identity.candidateId),
            metadataRow("run_index", identity.runIndex.toString()),
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
            metadataRow("supported_abis", Build.SUPPORTED_ABIS.joinToString("|")),
            metadataRow("ro.kernel.qemu", environment.emulatorDetection.kernelQemu),
            metadataRow("ro.boot.qemu", environment.emulatorDetection.bootQemu),
            metadataRow(
                "emulator_signals",
                environment.emulatorDetection.signals
                    .ifEmpty { listOf("none") }
                    .joinToString("|"),
            ),
            metadataRow("frame_warmup_iterations", environment.frameWarmupIterations.toString()),
            metadataRow("frame_sample_count", environment.frameSampleCount.toString()),
            metadataRow("checkpoint_interval_samples", CHECKPOINT_INTERVAL.toString()),
            metadataRow("maximum_valid_thermal_status", MAXIMUM_VALID_THERMAL_STATUS.toString()),
            metadataRow("deadline_source", "FrameMetrics.DEADLINE"),
            metadataRow("timeline_identity_source", "FrameMetrics.FRAME_TIMELINE_VSYNC_ID"),
            metadataRow(
                "timing_boundary",
                "command start through completion of the first directly copied Window FrameMetrics record " +
                    "whose canonical EditorScreen draw marker has the exact generation and EditorRenderState; " +
                    "image capture and correctness assertions are outside timing",
            ),
            metadataRow(
                "frame_completion_definition",
                "FrameMetrics.INTENDED_VSYNC_TIMESTAMP plus FrameMetrics.TOTAL_DURATION",
            ),
            metadataRow(
                "validity_boundary",
                "zero FrameMetrics report drops, stable active display mode, thermal status <= 1, " +
                    "power-save disabled, interactive display, and USB power at every checkpoint",
            ),
        )

    private fun checkpointRow(
        environment: P2AndroidMeasurementEnvironment,
        identity: P2FrameRunIdentity,
        checkpoint: P2FrameDeviceCheckpoint,
    ): String =
        rowByColumn(
            "record_type" to "checkpoint",
            "name" to checkpoint.name,
            "evidence_class" to environment.evidenceClass,
            "physical_profile_id" to environment.profileId,
            "candidate_id" to identity.candidateId,
            "run_index" to identity.runIndex,
            "sample_index" to checkpoint.sampleIndex,
            "display_mode_id" to checkpoint.displayModeId,
            "display_width_pixels" to checkpoint.physicalWidthPixels,
            "display_height_pixels" to checkpoint.physicalHeightPixels,
            "refresh_rate_hertz" to checkpoint.refreshRateHertz,
            "thermal_status" to checkpoint.thermalStatus,
            "power_save_mode" to checkpoint.powerSaveMode,
            "interactive" to checkpoint.interactive,
            "usb_powered" to checkpoint.usbPowered,
            "battery_level_percent" to checkpoint.batteryLevelPercent,
        )

    private fun sampleRow(
        environment: P2AndroidMeasurementEnvironment,
        identity: P2FrameRunIdentity,
        sample: P2AndroidFrameMeasurementSample,
    ): String {
        val marker = sample.correlatedFrame.marker
        val metrics = sample.correlatedFrame.metrics
        return rowByColumn(
            "record_type" to "sample",
            "name" to "apply_dense_stroke_first_correct_frame",
            "evidence_class" to environment.evidenceClass,
            "physical_profile_id" to environment.profileId,
            "candidate_id" to identity.candidateId,
            "run_index" to identity.runIndex,
            "canvas_width" to CANVAS_EDGE,
            "canvas_height" to CANVAS_EDGE,
            "position_count" to POSITION_COUNT,
            "warmup_iterations" to environment.frameWarmupIterations,
            "sample_count" to environment.frameSampleCount,
            "sample_index" to sample.sampleIndex,
            "generation" to sample.generation,
            "command_start_nanos" to sample.commandStartNanos,
            "command_finish_nanos" to sample.commandFinishNanos,
            "render_state_published_nanos" to sample.renderStatePublishedNanos,
            "draw_callback_nanos" to marker.callbackNanos,
            "drawing_time_millis" to marker.drawingTimeMillis,
            "frame_completion_nanos" to sample.frameCompletionNanos,
            "command_to_first_correct_frame_nanos" to sample.commandToFirstCorrectFrameNanos,
            "intended_vsync_nanos" to metrics.intendedVsyncNanos,
            "vsync_nanos" to metrics.vsyncNanos,
            "frame_timeline_vsync_id" to metrics.frameTimelineVsyncId,
            "total_duration_nanos" to metrics.totalDurationNanos,
            "deadline_nanos" to metrics.deadlineNanos,
            "deadline_met" to metrics.deadlineMet,
            "overrun_nanos" to metrics.overrunNanos,
            "gpu_duration_nanos" to metrics.gpuDurationNanos,
            "draw_duration_nanos" to metrics.drawDurationNanos,
            "layout_measure_duration_nanos" to metrics.layoutMeasureDurationNanos,
            "sync_duration_nanos" to metrics.syncDurationNanos,
            "command_issue_duration_nanos" to metrics.commandIssueDurationNanos,
            "swap_buffers_duration_nanos" to metrics.swapBuffersDurationNanos,
            "input_handling_duration_nanos" to metrics.inputHandlingDurationNanos,
            "unknown_delay_duration_nanos" to metrics.unknownDelayDurationNanos,
            "dropped_reports" to metrics.droppedReports,
            "first_draw_frame" to metrics.firstDrawFrame,
            "exact_state" to marker.exactState,
            "expected_revision" to sample.expectedRevision,
            "actual_revision" to marker.revision,
            "expected_snapshot_hash" to sample.expectedSnapshotHash,
            "actual_snapshot_hash" to marker.snapshotHash,
            "image_width" to sample.correctness.imageWidth,
            "image_height" to sample.correctness.imageHeight,
            "checked_logical_pixels" to sample.correctness.checkedLogicalPixels,
            "image_mismatch_count" to sample.correctness.mismatchCount,
            "sampled_argb_hash" to sample.correctness.sampledArgbHash,
        )
    }

    private fun metadataRow(
        name: String,
        value: String,
    ): String = rowByColumn("record_type" to "metadata", "name" to name, "value" to value)

    private fun rowByColumn(vararg values: Pair<String, Any>): String {
        val valuesByColumn = values.toMap()
        check(valuesByColumn.keys.all(COLUMNS::contains)) { "Unknown frame measurement report column." }
        return csvRow(*COLUMNS.map { column -> valuesByColumn[column] ?: "" }.toTypedArray())
    }

    private fun csvRow(vararg values: Any): String =
        values.joinToString(",") { value -> "\"${value.toString().replace("\"", "\"\"")}\"" }

    internal const val CHECKPOINT_INTERVAL: Int = 25
    internal const val MAXIMUM_VALID_THERMAL_STATUS: Int = 1
    private const val SCHEMA: String = "nene-pixel-p2-android-frame-measurement-v1"
    private const val CANVAS_EDGE: Int = 256
    private const val POSITION_COUNT: Int = CANVAS_EDGE * CANVAS_EDGE
    private val COLUMNS: List<String> =
        listOf(
            "record_type",
            "name",
            "value",
            "evidence_class",
            "physical_profile_id",
            "candidate_id",
            "run_index",
            "canvas_width",
            "canvas_height",
            "position_count",
            "warmup_iterations",
            "sample_count",
            "sample_index",
            "generation",
            "command_start_nanos",
            "command_finish_nanos",
            "render_state_published_nanos",
            "draw_callback_nanos",
            "drawing_time_millis",
            "frame_completion_nanos",
            "command_to_first_correct_frame_nanos",
            "intended_vsync_nanos",
            "vsync_nanos",
            "frame_timeline_vsync_id",
            "total_duration_nanos",
            "deadline_nanos",
            "deadline_met",
            "overrun_nanos",
            "gpu_duration_nanos",
            "draw_duration_nanos",
            "layout_measure_duration_nanos",
            "sync_duration_nanos",
            "command_issue_duration_nanos",
            "swap_buffers_duration_nanos",
            "input_handling_duration_nanos",
            "unknown_delay_duration_nanos",
            "dropped_reports",
            "first_draw_frame",
            "exact_state",
            "expected_revision",
            "actual_revision",
            "expected_snapshot_hash",
            "actual_snapshot_hash",
            "image_width",
            "image_height",
            "checked_logical_pixels",
            "image_mismatch_count",
            "sampled_argb_hash",
            "display_mode_id",
            "display_width_pixels",
            "display_height_pixels",
            "refresh_rate_hertz",
            "thermal_status",
            "power_save_mode",
            "interactive",
            "usb_powered",
            "battery_level_percent",
        )
}
