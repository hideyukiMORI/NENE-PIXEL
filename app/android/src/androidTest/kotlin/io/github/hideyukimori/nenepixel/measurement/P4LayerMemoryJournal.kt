package io.github.hideyukimori.nenepixel.measurement

import android.app.ActivityManager
import android.os.Bundle
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry

internal class P4LayerMemoryJournal(
    val admission: P4LayerRunAdmission,
    val runIndex: Int,
    private val environment: P2AndroidMeasurementEnvironment,
) {
    private val samples = mutableListOf<PostGcMemorySnapshot>()
    val comparisonRole = if (admission.artifactRole == "baseline_layers16") "baseline" else "candidate"
    val family = "$comparisonRole-layer-editor-retention"
    val fixtureName = "i89-145-${admission.preflightSha256.take(12)}-$comparisonRole-$runIndex.nenepixel"

    fun start() {
        require(runIndex in 1..5)
        check(admission.slotId == "memory-layers16-$comparisonRole-$runIndex")
        if (comparisonRole ==
            "baseline"
        ) {
            check(admission.productionCommit == "169b59287ca60e77e07ac91690450dd1a44b9ba4")
        }
        check(environment.profileId == PROFILE && !environment.emulatorDetection.isEmulator)
        check(!environment.auxiliaryEmulatorArgumentPresent)
        val processId = Process.myPid()
        val processStart = Process.getStartElapsedRealtime()
        val maximumHeap = Runtime.getRuntime().maxMemory()
        val memoryClass = environment.targetContext.getSystemService(ActivityManager::class.java).memoryClass
        check(processId > 0 && processStart > 0 && maximumHeap > 0 && memoryClass > 0)
        InstrumentationRegistry.getInstrumentation().sendStatus(
            3,
            Bundle().apply {
                putString("p4MemoryFamily", family)
                putString("p4MemoryBuildCommit", admission.buildCommit)
                putInt("p4MemoryRunIndex", runIndex)
                putInt("p4MemoryProcessId", processId)
                putLong("p4MemoryProcessStartElapsedRealtimeMillis", processStart)
                putLong("p4MemoryRuntimeMaxMemoryBytes", maximumHeap)
                putInt("p4MemoryClassMebibytes", memoryClass)
                putString("p4LayerIdentity", line("P4_LAYER_IDENTITY", admission.reportFields()))
            },
        )
    }

    fun capture(session: P4LayerEditorSession) {
        check(samples.size < CHECKPOINTS.size)
        val index = samples.size
        val sample =
            if (index == 0) {
                PostGcMemorySnapshot.captureBaseline(session.model)
            } else {
                PostGcMemorySnapshot.captureRetainedMemory(session.model)
            }
        samples.add(sample)
        val fields =
            listOf(
                "run" to runIndex.toString(),
                "slot_id" to admission.slotId,
                "index" to index.toString(),
                "checkpoint" to CHECKPOINTS[index],
                "java_bytes" to sample.javaHeapUsedBytes.toString(),
                "java_committed_bytes" to sample.javaHeapCommittedBytes.toString(),
                "pss_kib" to sample.totalPssKilobytes.toString(),
                "dalvik_pss_kib" to sample.dalvikPssKilobytes.toString(),
                "native_pss_kib" to sample.nativePssKilobytes.toString(),
                "other_pss_kib" to sample.otherPssKilobytes.toString(),
                "private_dirty_kib" to sample.totalPrivateDirtyKilobytes.toString(),
                "shared_dirty_kib" to sample.totalSharedDirtyKilobytes.toString(),
            )
        send(5, "p4LayerMemoryCheckpoint", line("P4_LAYER_CHECKPOINT", fields))
    }

    fun finish(fixture: P4GrantedDocument) {
        check(samples.size == CHECKPOINTS.size)
        val fields =
            admission.reportFields() +
                listOf(
                    "schema" to "nene-pixel-p4-layer-editor-retention-v1",
                    "family" to family,
                    "run" to runIndex.toString(),
                    "run_status" to "valid",
                    "profile" to PROFILE,
                    "fixture_sha256" to P4LayerFixture.MAXIMUM.sha256,
                    "fixture_uri" to fixture.uri.toString(),
                    "grantee_uid" to fixture.granteeUid.toString(),
                    "provider_uid" to fixture.providerUid.toString(),
                    "checkpoint_count" to "5",
                    "document_layers" to "16",
                    "canvas_width" to "256",
                    "canvas_height" to "256",
                    "preview_move_count" to "16",
                    "preview_raw_position_count" to "4081",
                    "preview_unique_position_count" to "256",
                    "undo_redo_cycles" to "10",
                    "gc_passes" to "2",
                    "published_position_verified" to "true",
                    "owner_inventory" to "main_activity:1,viewmodel:1,compose_canvas:1",
                ) +
                samples.flatMapIndexed { index, sample ->
                    listOf(
                        "${CHECKPOINTS[index]}_java_bytes" to sample.javaHeapUsedBytes.toString(),
                        "${CHECKPOINTS[index]}_pss_kib" to sample.totalPssKilobytes.toString(),
                    )
                }
        send(4, "p4MemoryReport", line("P4_LAYER_EDITOR_RETENTION", fields))
    }

    private fun send(
        code: Int,
        key: String,
        text: String,
    ) {
        InstrumentationRegistry.getInstrumentation().sendStatus(code, Bundle().apply { putString(key, text) })
    }

    private fun line(
        prefix: String,
        fields: List<Pair<String, String>>,
    ): String = prefix + " " + fields.joinToString(" ") { (key, value) -> "$key=$value" }

    companion object {
        const val PROFILE: String = "NENE-P2-ALLDOCUBE-IPL80MP-A16-API36"
        val CHECKPOINTS: List<String> =
            listOf("empty_idle", "maximum_loaded_idle", "long_preview_held", "committed_idle", "post_cycles_idle")
    }
}
