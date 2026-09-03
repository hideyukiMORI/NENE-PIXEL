package io.github.hideyukimori.nenepixel.measurement

import android.app.ActivityManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Process
import androidx.core.graphics.get
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
internal class P2AndroidCandidateMemoryMeasurementTest {
    @Test
    fun collectOneIndependentRetainedMemoryRun() {
        val environment = P2AndroidMeasurementEnvironment.fromRunnerArguments()
        val identity = P2AndroidRunIdentity.fromRunnerArguments()
        val candidate = P2RetainedCandidate.resolve(identity)
        validate(environment, identity)
        val display = P2AndroidPhysicalCheckpointCapture.defaultDisplay(environment.targetContext)
        val before =
            P2AndroidPhysicalCheckpointCapture
                .capture(environment.targetContext, display, "before_baseline", 0)
                .also(P2AndroidPhysicalCheckpoint::assertInitialValidity)

        candidate.preload()
        val baseline = PostGcMemorySnapshot.captureBaseline(BASELINE_MARKER)
        val owner = candidate.prepareAndVerify()
        val retained = PostGcMemorySnapshot.captureRetainedMemory(owner)
        val after =
            P2AndroidPhysicalCheckpointCapture
                .capture(environment.targetContext, display, "after_retained", 1)
                .also { checkpoint -> checkpoint.assertCompatibleWith(before) }
        val output = write(environment, identity, candidate, before, after, baseline, retained)

        assertTrue(output.isFile)
        assertTrue(output.length() > 0L)
        println("P2_ANDROID_CANDIDATE_MEMORY_OUTPUT=${output.absolutePath}")
    }

    private fun validate(
        environment: P2AndroidMeasurementEnvironment,
        identity: P2AndroidRunIdentity,
    ) {
        check(!environment.emulatorDetection.isEmulator)
        check(!environment.auxiliaryEmulatorArgumentPresent)
        check(environment.profileId == P2AndroidFinalCommandProtocol.PHYSICAL_PROFILE_ID)
        check(identity.runIndex in RUN_INDEX_RANGE)
        P2AndroidFinalCommandProfile.validateRuntime(environment.targetContext)
    }

    private fun write(
        environment: P2AndroidMeasurementEnvironment,
        identity: P2AndroidRunIdentity,
        candidate: P2RetainedCandidate,
        before: P2AndroidPhysicalCheckpoint,
        after: P2AndroidPhysicalCheckpoint,
        baseline: PostGcMemorySnapshot,
        retained: PostGcMemorySnapshot,
    ): File {
        val output =
            File(
                environment.targetContext.filesDir,
                "p2-measurements/p2-android-candidate-memory-${candidate.fileId}-run-" +
                    "${identity.runIndex.toString().padStart(2, '0')}.csv",
            )
        val rows =
            metadataRows(environment, identity, candidate) +
                physicalRow(identity, before) +
                memoryRow(identity, "baseline_post_gc", 0, baseline) +
                memoryRow(identity, "retained_post_gc", 1, retained) +
                physicalRow(identity, after) +
                summaryRow(identity, baseline, retained)
        return P2AndroidMemoryCsv.writeImmutable(output, rows)
    }

    private fun metadataRows(
        environment: P2AndroidMeasurementEnvironment,
        identity: P2AndroidRunIdentity,
        candidate: P2RetainedCandidate,
    ): List<P2AndroidMemoryCsvRow> {
        val activityManager = environment.targetContext.getSystemService(ActivityManager::class.java)
        return listOf(
            metadata(identity, "schema", SCHEMA),
            metadata(identity, "candidate_id", candidate.candidateId),
            metadata(identity, "source_commit", identity.sourceCommit),
            metadata(identity, "build_fingerprint", Build.FINGERPRINT),
            metadata(identity, "runtime_max_memory_bytes", Runtime.getRuntime().maxMemory()),
            metadata(identity, "memory_class_mib", activityManager.memoryClass),
            metadata(identity, "retained_workload", WORKLOAD_ID),
            metadata(identity, "gc_boundary", GC_BOUNDARY),
        )
    }

    private fun metadata(
        identity: P2AndroidRunIdentity,
        name: String,
        value: Any,
    ): P2AndroidMemoryCsvRow =
        P2AndroidMemoryCsv.row(
            "record_type" to "metadata",
            "name" to name,
            "value" to value,
            *identityValues(identity).toTypedArray(),
        )

    private fun physicalRow(
        identity: P2AndroidRunIdentity,
        checkpoint: P2AndroidPhysicalCheckpoint,
    ): P2AndroidMemoryCsvRow =
        P2AndroidMemoryCsv.row(
            "record_type" to "physical_checkpoint",
            "name" to checkpoint.name,
            *identityValues(identity).toTypedArray(),
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
        identity: P2AndroidRunIdentity,
        name: String,
        index: Int,
        memory: PostGcMemorySnapshot,
    ): P2AndroidMemoryCsvRow =
        P2AndroidMemoryCsv.row(
            "record_type" to "memory_checkpoint",
            "name" to name,
            *identityValues(identity).toTypedArray(),
            *workloadValues().toTypedArray(),
            "memory_checkpoint_index" to index,
            "post_gc_java_heap_used_bytes" to memory.javaHeapUsedBytes,
            "post_gc_java_heap_committed_bytes" to memory.javaHeapCommittedBytes,
            "runtime_max_memory_bytes" to Runtime.getRuntime().maxMemory(),
            "total_pss_kb" to memory.totalPssKilobytes,
            "dalvik_pss_kb" to memory.dalvikPssKilobytes,
            "native_pss_kb" to memory.nativePssKilobytes,
            "other_pss_kb" to memory.otherPssKilobytes,
            "total_private_dirty_kb" to memory.totalPrivateDirtyKilobytes,
            "total_shared_dirty_kb" to memory.totalSharedDirtyKilobytes,
            "correctness_status" to PASS,
            "boundary" to if (index == 0) BASELINE_BOUNDARY else RETAINED_BOUNDARY,
        )

    private fun summaryRow(
        identity: P2AndroidRunIdentity,
        baseline: PostGcMemorySnapshot,
        retained: PostGcMemorySnapshot,
    ): P2AndroidMemoryCsvRow =
        P2AndroidMemoryCsv.row(
            "record_type" to "retained_summary",
            "name" to WORKLOAD_ID,
            *identityValues(identity).toTypedArray(),
            *workloadValues().toTypedArray(),
            "final_revision" to P2AndroidMemoryProtocol.FINAL_REVISION,
            "baseline_java_heap_used_bytes" to baseline.javaHeapUsedBytes,
            "baseline_java_heap_committed_bytes" to baseline.javaHeapCommittedBytes,
            "baseline_total_pss_kb" to baseline.totalPssKilobytes,
            "retained_java_heap_used_bytes" to retained.javaHeapUsedBytes,
            "retained_java_heap_committed_bytes" to retained.javaHeapCommittedBytes,
            "retained_total_pss_kb" to retained.totalPssKilobytes,
            "paired_pss_delta_kb" to retained.totalPssKilobytes - baseline.totalPssKilobytes,
            "candidate_retained_memory_status" to PASS,
            "candidate_projection_status" to PASS,
            "correctness_status" to PASS,
            "boundary" to RETAINED_BOUNDARY,
        )

    private fun identityValues(identity: P2AndroidRunIdentity): List<Pair<String, Any>> =
        listOf(
            "schema" to SCHEMA,
            "evidence_class" to "physical_device",
            "physical_profile_id" to P2AndroidFinalCommandProtocol.PHYSICAL_PROFILE_ID,
            "candidate_id" to identity.candidateId,
            "workload_id" to WORKLOAD_ID,
            "run_index" to identity.runIndex,
            "source_commit" to identity.sourceCommit,
            "process_id" to Process.myPid(),
            "process_start_elapsed_realtime_ms" to Process.getStartElapsedRealtime(),
        )

    private fun workloadValues(): List<Pair<String, Any>> =
        listOf(
            "canvas_width" to P2AndroidMemoryProtocol.CANVAS_EDGE,
            "canvas_height" to P2AndroidMemoryProtocol.CANVAS_EDGE,
            "pixel_count" to P2AndroidMemoryProtocol.PIXEL_COUNT,
            "history_entries" to P2AndroidMemoryProtocol.HISTORY_ENTRIES,
            "change_count_per_entry" to P2AndroidMemoryProtocol.CHANGE_COUNT_PER_ENTRY,
            "total_retained_changes" to P2AndroidMemoryProtocol.TOTAL_RETAINED_CHANGES,
        )

    private companion object {
        const val SCHEMA: String = "nene-pixel-p2-android-candidate-retained-memory-v1"
        const val WORKLOAD_ID: String = "retained_bitmap_256_square_h64_t8n"
        const val PASS: String = "pass"
        const val GC_BOUNDARY: String =
            "two GC/finalization passes only at baseline and retained checkpoints in an independent process"
        const val BASELINE_BOUNDARY: String = "post-GC baseline after candidate preload and before retained fixture"
        const val RETAINED_BOUNDARY: String =
            "post-GC process memory retaining final snapshot, 64 transitions, shared inverse policy, and bitmap"
        val BASELINE_MARKER: Any = Any()
        val RUN_INDEX_RANGE: IntRange = 1..3
    }
}

private enum class P2RetainedCandidate(
    val candidateId: String,
    val fileId: String,
) {
    Current("current-canonical-object-bitmap-v1", "current"),
    FlatPacked("flat-packed-rgba8888-shared-inverse-bitmap-v1", "flat-packed"),
    ;

    fun preload() {
        when (this) {
            Current -> P2AndroidMemoryRetainedWorkload.preload()
            FlatPacked -> P2PackedRetainedMemoryWorkload.preload()
        }
    }

    fun prepareAndVerify(): Any =
        when (this) {
            Current -> P2AndroidMemoryRetainedWorkload.prepareAndVerify()
            FlatPacked -> P2PackedRetainedMemoryWorkload.prepareAndVerify()
        }

    companion object {
        fun resolve(identity: P2AndroidRunIdentity): P2RetainedCandidate =
            requireNotNull(entries.singleOrNull { candidate -> candidate.candidateId == identity.candidateId }) {
                "Unknown retained-memory candidate '${identity.candidateId}'."
            }
    }
}

private data class P2PackedRetainedEntry(
    val forward: P2PackedCandidatePatch,
    val reverse: P2PackedCandidatePatch,
)

private data class P2PackedRetainedOwner(
    val snapshot: P2PackedCandidateSnapshot,
    val entries: List<P2PackedRetainedEntry>,
    val bitmap: Bitmap,
)

private object P2PackedRetainedMemoryWorkload {
    fun preload() {
        val initial = P2FlatPackedCandidateSnapshot.initial(IntArray(PIXEL_COUNT) { WHITE_RGBA })
        val patch = requireNotNull(P2PackedCandidatePatch.fromRawPath(initial, intArrayOf(0), RED_RGBA))
        check(initial.apply(patch).packedAt(0) == RED_RGBA)
    }

    fun prepareAndVerify(): P2PackedRetainedOwner {
        var current: P2PackedCandidateSnapshot =
            P2FlatPackedCandidateSnapshot.initial(IntArray(PIXEL_COUNT) { WHITE_RGBA })
        val entries = ArrayList<P2PackedRetainedEntry>(HISTORY_ENTRIES)
        repeat(HISTORY_ENTRIES) { entryIndex ->
            val target = if (entryIndex / BLOCK_COUNT % 2 == 0) RED_RGBA else WHITE_RGBA
            val block = entryIndex % BLOCK_COUNT
            val positions = IntArray(CHANGES_PER_ENTRY) { offset -> block * CHANGES_PER_ENTRY + offset }
            val forward = requireNotNull(P2PackedCandidatePatch.fromRawPath(current, positions, target))
            val reverse = forward.inverse()
            val applied = current.apply(forward)
            applied.apply(reverse).verifyEquivalent(current)
            entries += P2PackedRetainedEntry(forward, reverse)
            current = applied
        }
        check(current.revision == P2AndroidMemoryProtocol.FINAL_REVISION)
        current.verifyUniform(WHITE_RGBA)
        check(entries.all { entry -> entry.forward.changeCount == CHANGES_PER_ENTRY })
        val bitmap = current.toBitmap()
        check(bitmap.width == CANVAS_EDGE && bitmap.height == CANVAS_EDGE)
        check(bitmap[0, 0] == OPAQUE_WHITE_ARGB)
        check(bitmap[CANVAS_EDGE - 1, CANVAS_EDGE - 1] == OPAQUE_WHITE_ARGB)
        return P2PackedRetainedOwner(current, entries, bitmap)
    }

    private fun P2PackedCandidateSnapshot.toBitmap(): Bitmap {
        val colors = IntArray(PIXEL_COUNT) { index -> packedAt(index).rgbaToArgb() }
        return Bitmap.createBitmap(colors, CANVAS_EDGE, CANVAS_EDGE, Bitmap.Config.ARGB_8888)
    }

    private fun Int.rgbaToArgb(): Int =
        ((this and CHANNEL_MASK) shl ALPHA_SHIFT) or
            ((this ushr RED_SHIFT and CHANNEL_MASK) shl COMPOSE_RED_SHIFT) or
            ((this ushr GREEN_SHIFT and CHANNEL_MASK) shl COMPOSE_GREEN_SHIFT) or
            (this ushr BLUE_SHIFT and CHANNEL_MASK)

    private const val CANVAS_EDGE: Int = P2AndroidMemoryProtocol.CANVAS_EDGE
    private const val PIXEL_COUNT: Int = P2AndroidMemoryProtocol.PIXEL_COUNT
    private const val HISTORY_ENTRIES: Int = P2AndroidMemoryProtocol.HISTORY_ENTRIES
    private const val CHANGES_PER_ENTRY: Int = P2AndroidMemoryProtocol.CHANGE_COUNT_PER_ENTRY
    private const val BLOCK_COUNT: Int = P2AndroidMemoryProtocol.BLOCK_COUNT
    private const val WHITE_RGBA: Int = -1
    private const val RED_RGBA: Int = -0x00ffff01
    private const val OPAQUE_WHITE_ARGB: Int = -1
    private const val CHANNEL_MASK: Int = 0xff
    private const val ALPHA_SHIFT: Int = 24
    private const val RED_SHIFT: Int = 24
    private const val GREEN_SHIFT: Int = 16
    private const val BLUE_SHIFT: Int = 8
    private const val COMPOSE_RED_SHIFT: Int = 16
    private const val COMPOSE_GREEN_SHIFT: Int = 8
}
