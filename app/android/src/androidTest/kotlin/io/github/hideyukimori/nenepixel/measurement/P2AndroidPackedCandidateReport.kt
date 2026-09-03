package io.github.hideyukimori.nenepixel.measurement

import android.app.ActivityManager
import android.os.Build
import java.io.File

internal data class P2AndroidPackedCandidateSample(
    val spec: P2PackedCandidateSpec,
    val sampleIndex: Int,
    val execution: P2MeasuredPackedCandidateExecution,
)

internal object P2AndroidPackedCandidateReport {
    fun write(
        environment: P2AndroidMeasurementEnvironment,
        identity: P2AndroidRunIdentity,
        samples: List<P2AndroidPackedCandidateSample>,
    ): File {
        val output = P2AndroidPackedCandidateProtocol.outputFile(environment)
        return P2AndroidFinalCommandOutputPublication.publish(
            output = output,
            policy = P2AndroidFinalCommandPlan.PublicationPolicy.FailIfExists,
            writeRows = { target -> writeRows(target, environment, identity, samples) },
        )
    }

    private fun writeRows(
        output: File,
        environment: P2AndroidMeasurementEnvironment,
        identity: P2AndroidRunIdentity,
        samples: List<P2AndroidPackedCandidateSample>,
    ) {
        val activityManager = environment.targetContext.getSystemService(ActivityManager::class.java)
        output.bufferedWriter().use { writer ->
            writer.appendLine(COLUMNS.csvRow())
            writer.appendLine(metadata("schema", P2AndroidPackedCandidateProtocol.SCHEMA))
            writer.appendLine(metadata("physical_profile_id", environment.profileId))
            writer.appendLine(metadata("source_commit", identity.sourceCommit))
            writer.appendLine(metadata("manufacturer", Build.MANUFACTURER))
            writer.appendLine(metadata("model", Build.MODEL))
            writer.appendLine(metadata("build_fingerprint", Build.FINGERPRINT))
            writer.appendLine(metadata("memory_class_mib", activityManager.memoryClass.toString()))
            writer.appendLine(metadata("canvas", "256x256"))
            writer.appendLine(metadata("warmups", P2AndroidPackedCandidateProtocol.WARMUP_ITERATIONS.toString()))
            writer.appendLine(
                metadata("samples_per_workload", P2AndroidPackedCandidateProtocol.SAMPLES_PER_WORKLOAD.toString()),
            )
            writer.appendLine(
                metadata(
                    "boundary",
                    "candidate operation between nanoTime calls; fixture creation and full correctness outside; " +
                        "no forced GC, finalization, heap, PSS, document hash, or pixel digest between samples",
                ),
            )
            samples.forEach { sample -> writer.appendLine(sample.row()) }
        }
    }

    private fun P2AndroidPackedCandidateSample.row(): String =
        listOf(
            "sample",
            spec.candidate.candidateId,
            spec.workload.metricName,
            sampleIndex,
            execution.latencyNanos,
            execution.runtimeDelta.allocatedBytesDelta,
            execution.runtimeDelta.gcCountDelta,
            execution.runtimeDelta.gcTimeMillisDelta,
            execution.runtimeDelta.blockingGcCountDelta,
            execution.runtimeDelta.blockingGcTimeMillisDelta,
            execution.outcome.revision,
            execution.outcome.firstPixel.toUnsignedHex(),
            execution.outcome.lastPixel.toUnsignedHex(),
            execution.outcome.changeCount,
        ).csvRow()

    private fun metadata(
        name: String,
        value: String,
    ): String = listOf("metadata", name, value).csvRow(COLUMNS.size)

    private fun List<Any>.csvRow(width: Int = size): String =
        List(width) { index -> getOrElse(index) { "" } }.joinToString(",") { value ->
            "\"${value.toString().replace("\"", "\"\"")}\""
        }

    private fun Int.toUnsignedHex(): String = toUInt().toString(radix = HEX_RADIX).padStart(HEX_DIGITS, '0')

    private const val HEX_RADIX: Int = 16
    private const val HEX_DIGITS: Int = 8
    private val COLUMNS: List<Any> =
        listOf(
            "record_type",
            "candidate_or_name",
            "workload_or_value",
            "sample_index",
            "latency_nanos",
            "art_allocated_bytes_delta",
            "art_gc_count_delta",
            "art_gc_time_ms_delta",
            "art_blocking_gc_count_delta",
            "art_blocking_gc_time_ms_delta",
            "revision_after",
            "first_rgba",
            "last_rgba",
            "change_count",
        )
}
