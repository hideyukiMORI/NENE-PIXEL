package io.github.hideyukimori.nenepixel.measurement

import android.app.ActivityManager
import android.os.Build
import android.os.Debug
import java.io.File

internal data class P2AndroidMeasurementSample(
    val spec: P2CommandWorkloadSpec,
    val sampleIndex: Int,
    val latencyNanos: Long,
    val outcome: CommandOutcomeDescriptor,
    val runtimeDelta: ArtRuntimeDelta,
    val memory: PostGcMemorySnapshot,
)

internal object P2AndroidMeasurementReport {
    fun write(
        environment: P2AndroidMeasurementEnvironment,
        baseline: PostGcMemorySnapshot,
        samples: List<P2AndroidMeasurementSample>,
    ): File {
        val output = environment.outputFile
        val outputDirectory = requireNotNull(output.parentFile)
        check(outputDirectory.isDirectory || outputDirectory.mkdirs()) {
            "Failed to create measurement output directory."
        }
        output.bufferedWriter().use { writer ->
            writer.appendLine(csvRow(*COLUMNS.toTypedArray()))
            metadataRows(environment).forEach(writer::appendLine)
            runtimeStatRows().forEach(writer::appendLine)
            writer.appendLine(baselineRow(environment, baseline))
            samples.forEach { sample -> writer.appendLine(sampleRow(environment, sample)) }
        }
        return output
    }

    private fun metadataRows(environment: P2AndroidMeasurementEnvironment): List<String> {
        val activityManager = environment.targetContext.getSystemService(ActivityManager::class.java)
        return listOf(
            metadataRow("schema", SCHEMA),
            metadataRow("evidence_class", environment.evidenceClass),
            metadataRow("physical_profile_id", environment.profileId),
            metadataRow("manufacturer", Build.MANUFACTURER),
            metadataRow("model", Build.MODEL),
            metadataRow("product", Build.PRODUCT),
            metadataRow("hardware", Build.HARDWARE),
            metadataRow("api_level", Build.VERSION.SDK_INT.toString()),
            metadataRow("build_fingerprint", Build.FINGERPRINT),
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
            metadataRow("warmup_iterations", environment.warmupIterations.toString()),
            metadataRow("sample_count_per_workload", environment.sampleCount.toString()),
            metadataRow("canvas_edges", "16|64|256"),
            metadataRow(
                "measurement_boundary",
                "CommandGateway.execute latency; approximate process ART runtime-stat delta between Debug snapshots " +
                    "includes snapshot overhead; fixture and correctness assertions excluded",
            ),
            metadataRow(
                "memory_boundary",
                "post-verification explicit Java GC while retaining current gateway, document, " +
                    "and one-level history; " +
                    "then Runtime Java heap and Debug.MemoryInfo process PSS",
            ),
        )
    }

    private fun runtimeStatRows(): List<String> {
        val runtimeStats: Map<*, *> = Debug.getRuntimeStats()
        return runtimeStats.entries
            .mapNotNull { entry ->
                val name = entry.key as? String ?: return@mapNotNull null
                val value = entry.value as? String ?: return@mapNotNull null
                name to value
            }.sortedBy { (name, _) -> name }
            .map { (name, value) -> metadataRow("runtime_stat_end:$name", value) }
    }

    private fun sampleRow(
        environment: P2AndroidMeasurementEnvironment,
        sample: P2AndroidMeasurementSample,
    ): String =
        csvRow(
            "sample",
            sample.spec.kind.metricName,
            "",
            environment.evidenceClass,
            environment.profileId,
            sample.spec.canvasEdge,
            sample.spec.canvasEdge,
            sample.spec.positionCount,
            environment.warmupIterations,
            sample.sampleIndex,
            sample.latencyNanos,
            sample.outcome.resultKind,
            sample.outcome.revision,
            sample.outcome.history,
            sample.outcome.documentHash,
            sample.runtimeDelta.allocatedBytesBefore,
            sample.runtimeDelta.allocatedBytesAfter,
            sample.runtimeDelta.allocatedBytesDelta,
            sample.runtimeDelta.gcCountDelta,
            sample.runtimeDelta.gcTimeMillisDelta,
            sample.runtimeDelta.blockingGcCountDelta,
            sample.runtimeDelta.blockingGcTimeMillisDelta,
            sample.memory.javaHeapUsedBytes,
            sample.memory.javaHeapCommittedBytes,
            sample.memory.totalPssKilobytes,
            sample.memory.dalvikPssKilobytes,
            sample.memory.nativePssKilobytes,
            sample.memory.otherPssKilobytes,
            sample.memory.totalPrivateDirtyKilobytes,
            sample.memory.totalSharedDirtyKilobytes,
            SAMPLE_ASSERTION_BOUNDARY,
        )

    private fun baselineRow(
        environment: P2AndroidMeasurementEnvironment,
        memory: PostGcMemorySnapshot,
    ): String =
        rowByColumn(
            "record_type" to "baseline",
            "name" to "process_post_gc_before_workloads",
            "evidence_class" to environment.evidenceClass,
            "physical_profile_id" to environment.profileId,
            "post_gc_java_heap_used_bytes" to memory.javaHeapUsedBytes,
            "post_gc_java_heap_committed_bytes" to memory.javaHeapCommittedBytes,
            "total_pss_kb" to memory.totalPssKilobytes,
            "dalvik_pss_kb" to memory.dalvikPssKilobytes,
            "native_pss_kb" to memory.nativePssKilobytes,
            "other_pss_kb" to memory.otherPssKilobytes,
            "total_private_dirty_kb" to memory.totalPrivateDirtyKilobytes,
            "total_shared_dirty_kb" to memory.totalSharedDirtyKilobytes,
            "boundary" to "post-GC process baseline captured before workload fixture creation and warmup",
        )

    private fun metadataRow(
        name: String,
        value: String,
    ): String =
        csvRow(
            "metadata",
            name,
            value,
            *Array(COLUMNS.size - METADATA_PREFIX_COLUMNS) { "" },
        )

    private fun csvRow(vararg values: Any): String =
        values.joinToString(",") { value -> "\"${value.toString().replace("\"", "\"\"")}\"" }

    private fun rowByColumn(vararg values: Pair<String, Any>): String {
        val valuesByColumn = values.toMap()
        check(valuesByColumn.keys.all(COLUMNS::contains)) { "Unknown measurement report column." }
        return csvRow(*COLUMNS.map { column -> valuesByColumn[column] ?: "" }.toTypedArray())
    }

    private const val SCHEMA: String = "nene-pixel-p2-android-command-measurement-v1"
    private const val METADATA_PREFIX_COLUMNS: Int = 3
    private const val SAMPLE_ASSERTION_BOUNDARY: String =
        "exact DocumentState, revision, history, ChangeSet revision, and typed no-op asserted before memory capture"
    private val COLUMNS: List<String> =
        listOf(
            "record_type",
            "name",
            "value",
            "evidence_class",
            "physical_profile_id",
            "canvas_width",
            "canvas_height",
            "position_count",
            "warmup_iterations",
            "sample_index",
            "latency_nanos",
            "result_kind",
            "revision_after",
            "history_after",
            "document_hash",
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
            "boundary",
        )
}
