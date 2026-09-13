package io.github.hideyukimori.nenepixel.measurement

import android.app.ActivityManager
import android.os.Bundle
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class P2ProductionHistoryRetentionMeasurementTest {
    @Test
    fun measureP4HistoryRetentionOnPhysicalProfile() {
        val environment = P2AndroidMeasurementEnvironment.fromRunnerArguments()
        val runIndex = requiredRunIndex()
        val buildCommit = requiredBuildCommit()
        val workload = requiredHistoryWorkload()
        reportProcessIdentity(runIndex, workload.family, buildCommit, environment)
        assertPhysicalEnvironment(environment, workload.family)
        val baseline = PostGcMemorySnapshot.captureBaseline(workload.gateway)

        workload.populate()
        workload.assertExactRoundTrip()
        workload.assertOwnerInventory()
        val retained = PostGcMemorySnapshot.captureRetainedMemory(workload.gateway)

        workload.exerciseUndoRedoCycles()
        workload.assertOwnerInventory()
        val afterCycles = PostGcMemorySnapshot.captureRetainedMemory(workload.gateway)
        val reportText = report(runIndex, buildCommit, environment, workload, baseline, retained, afterCycles)
        println(reportText)
        InstrumentationRegistry.getInstrumentation().sendStatus(
            HISTORY_REPORT_STATUS_CODE,
            Bundle().apply { putString(HISTORY_REPORT_BUNDLE_KEY, reportText) },
        )
    }

    private fun requiredHistoryWorkload(): P4HistoryRetentionWorkload =
        when (val family = requiredArgument(P4_MEMORY_FAMILY_ARGUMENT)) {
            P4_CANDIDATE_COMMON_HISTORY -> P4CommonIndexedHistoryWorkload()
            P4_CANDIDATE_PALETTE_HISTORY -> P4PaletteHistoryWorkload()
            else -> error("Unsupported P4 history-retention family: $family")
        }

    private fun reportProcessIdentity(
        runIndex: Int,
        family: String,
        buildCommit: String,
        environment: P2AndroidMeasurementEnvironment,
    ) {
        val processId = Process.myPid()
        val processStartElapsedRealtime = Process.getStartElapsedRealtime()
        val runtimeMaxMemoryBytes = Runtime.getRuntime().maxMemory()
        val memoryClassMebibytes =
            environment.targetContext
                .getSystemService(ActivityManager::class.java)
                .memoryClass
        check(processId > 0)
        check(processStartElapsedRealtime > 0L)
        check(runtimeMaxMemoryBytes > 0L)
        check(memoryClassMebibytes > 0)
        InstrumentationRegistry.getInstrumentation().sendStatus(
            PROCESS_IDENTITY_STATUS_CODE,
            Bundle().apply {
                putString("p4MemoryFamily", family)
                putString("p4MemoryBuildCommit", buildCommit)
                putInt("p4MemoryRunIndex", runIndex)
                putInt("p4MemoryProcessId", processId)
                putLong("p4MemoryProcessStartElapsedRealtimeMillis", processStartElapsedRealtime)
                putLong("p4MemoryRuntimeMaxMemoryBytes", runtimeMaxMemoryBytes)
                putInt("p4MemoryClassMebibytes", memoryClassMebibytes)
            },
        )
    }

    private fun requiredRunIndex(): Int {
        val runIndex = requiredArgument(P4_MEMORY_RUN_INDEX_ARGUMENT).toIntOrNull()
        return requireNotNull(runIndex?.takeIf { it in 1..P4_MEMORY_RUN_COUNT }) {
            "Runner argument '$P4_MEMORY_RUN_INDEX_ARGUMENT' must be in 1..$P4_MEMORY_RUN_COUNT."
        }
    }

    private fun requiredBuildCommit(): String {
        val commit = requiredArgument(P4_MEMORY_BUILD_COMMIT_ARGUMENT).lowercase()
        return commit.takeIf(P4_COMMIT_PATTERN::matches)
            ?: error("Runner argument '$P4_MEMORY_BUILD_COMMIT_ARGUMENT' must be a full Git commit.")
    }

    private fun requiredArgument(name: String): String =
        requireNotNull(
            InstrumentationRegistry
                .getArguments()
                .getString(name)
                ?.trim()
                ?.takeIf(String::isNotEmpty),
        ) { "Runner argument '$name' is required." }

    private fun assertPhysicalEnvironment(
        environment: P2AndroidMeasurementEnvironment,
        family: String,
    ) {
        check(!environment.emulatorDetection.isEmulator)
        check(!environment.auxiliaryEmulatorArgumentPresent)
        check(environment.profileId == P4_MEMORY_PROFILE)
        val display = P2AndroidPhysicalCheckpointCapture.defaultDisplay(environment.targetContext)
        P2AndroidPhysicalCheckpointCapture
            .capture(environment.targetContext, display, family, sampleIndex = 0)
            .assertInitialValidity()
    }

    private fun report(
        runIndex: Int,
        buildCommit: String,
        environment: P2AndroidMeasurementEnvironment,
        workload: P4HistoryRetentionWorkload,
        baseline: PostGcMemorySnapshot,
        retained: PostGcMemorySnapshot,
        afterCycles: PostGcMemorySnapshot,
    ): String =
        listOf(
            "P4_HISTORY_RETENTION",
            "schema=${workload.schema}",
            "family=${workload.family}",
            "run=$runIndex",
            "run_status=valid",
            "measurement_build_commit=$buildCommit",
            "profile=${environment.profileId}",
            "entries=64",
            "changes=524288",
            "logical_bytes=${workload.logicalBytes}",
            "owner_inventory=current_document:1,history:64",
            "baseline_java_bytes=${baseline.javaHeapUsedBytes}",
            "retained_java_bytes=${retained.javaHeapUsedBytes}",
            "retained_java_delta_bytes=${retained.javaHeapUsedBytes - baseline.javaHeapUsedBytes}",
            "after_cycles_java_bytes=${afterCycles.javaHeapUsedBytes}",
            "baseline_pss_kib=${baseline.totalPssKilobytes}",
            "retained_pss_kib=${retained.totalPssKilobytes}",
            "retained_pss_delta_kib=${retained.totalPssKilobytes - baseline.totalPssKilobytes}",
            "after_cycles_pss_kib=${afterCycles.totalPssKilobytes}",
        ).joinToString(separator = " ")

    private companion object {
        const val PROCESS_IDENTITY_STATUS_CODE: Int = 3
        const val HISTORY_REPORT_STATUS_CODE: Int = 4
        const val HISTORY_REPORT_BUNDLE_KEY: String = "p4MemoryReport"
        val P4_COMMIT_PATTERN: Regex = Regex("[0-9a-f]{40}")
        const val P4_MEMORY_PROFILE: String = "NENE-P2-ALLDOCUBE-IPL80MP-A16-API36"
    }
}
