package io.github.hideyukimori.nenepixel.measurement

import android.os.Build
import java.io.File

internal data class P2AndroidMemoryAggregateInput(
    val environment: P2AndroidMeasurementEnvironment,
    val identity: P2AndroidMemoryRunIdentity,
    val runs: List<P2AndroidMemoryAuditedRun>,
)

internal object P2AndroidMemoryAggregateReport {
    fun write(input: P2AndroidMemoryAggregateInput): File {
        validate(input)
        val summary = summary(input.runs)
        val rows = metadataRows(input) + input.runs.map { run -> runRow(input, run) } + aggregateRow(input, summary)
        return P2AndroidMemoryCsv.writeImmutable(input.environment.memoryAggregateOutputFile, rows)
    }

    private fun metadataRows(input: P2AndroidMemoryAggregateInput): List<P2AndroidMemoryCsvRow> =
        listOf(
            metadata(input, "schema", P2AndroidMemoryProtocol.AGGREGATE_SCHEMA),
            metadata(input, "output_identity", "device-memory"),
            metadata(input, "run_status", "valid"),
            metadata(input, "input_run_count", P2AndroidMemoryProtocol.RUN_COUNT),
            metadata(input, "build_fingerprint", Build.FINGERPRINT),
            metadata(input, "security_patch", Build.VERSION.SECURITY_PATCH),
            metadata(input, "paired_pss_formula", "d_i=retained_total_pss_kb_i-baseline_total_pss_kb_i"),
            metadata(input, "median_rule", "nearest_rank_five=third_sorted;no_interpolation;no_outlier_removal"),
            metadata(input, "pss_median_condition", "2*median(d_i)<=memoryClassKiB"),
            metadata(input, "pss_individual_condition", "5*d_i<=3*memoryClassKiB for every run"),
            metadata(input, "steady_art_live_heap_condition", "2*retained_java_heap_used_bytes<=Runtime.maxMemory"),
            metadata(input, "post_gc_churn_status", P2AndroidMemoryProtocol.CHURN_STATUS),
            metadata(input, "peak_headroom_status", UNEVALUATED),
            metadata(input, "candidate_retained_memory_status", UNEVALUATED),
            metadata(input, "candidate_projection_status", UNEVALUATED),
        )

    private fun metadata(
        input: P2AndroidMemoryAggregateInput,
        name: String,
        value: Any,
    ): P2AndroidMemoryCsvRow =
        P2AndroidMemoryCsv.row(
            "record_type" to "metadata",
            "name" to name,
            "value" to value,
            *aggregateIdentityValues(input).toTypedArray(),
        )

    private fun runRow(
        input: P2AndroidMemoryAggregateInput,
        run: P2AndroidMemoryAuditedRun,
    ): P2AndroidMemoryCsvRow {
        val runtime = run.runtime
        val memoryClassKilobytes = runtime.memoryClassMib * KIBIBYTES_PER_MEBIBYTE.toLong()
        val individualPssPass =
            PSS_INDIVIDUAL_MULTIPLIER * run.pairedPssDeltaKilobytes <=
                PSS_LIMIT_MULTIPLIER * memoryClassKilobytes
        val heapPass = HEAP_MULTIPLIER * runtime.retained.javaHeapUsedBytes <= runtime.runtimeMaxMemoryBytes
        return P2AndroidMemoryCsv.row(
            "record_type" to "run",
            "name" to "run_${run.process.runIndex.toString().padStart(2, '0')}",
            "schema" to P2AndroidMemoryProtocol.AGGREGATE_SCHEMA,
            "evidence_class" to input.environment.evidenceClass,
            "physical_profile_id" to input.environment.profileId,
            "candidate_id" to input.identity.run.candidateId,
            "workload_id" to P2AndroidMemoryProtocol.WORKLOAD_ID,
            "batch_id" to input.identity.batchId,
            "run_index" to run.process.runIndex,
            "source_commit" to input.identity.run.sourceCommit,
            "process_id" to run.process.processId,
            "process_start_elapsed_realtime_ms" to run.process.processStartElapsedRealtimeMillis,
            *workloadValues().toTypedArray(),
            "runtime_max_memory_bytes" to runtime.runtimeMaxMemoryBytes,
            "memory_class_mib" to runtime.memoryClassMib,
            "baseline_java_heap_used_bytes" to runtime.baseline.javaHeapUsedBytes,
            "baseline_java_heap_committed_bytes" to runtime.baseline.javaHeapCommittedBytes,
            "baseline_total_pss_kb" to runtime.baseline.totalPssKilobytes,
            "retained_java_heap_used_bytes" to runtime.retained.javaHeapUsedBytes,
            "retained_java_heap_committed_bytes" to runtime.retained.javaHeapCommittedBytes,
            "retained_total_pss_kb" to runtime.retained.totalPssKilobytes,
            "paired_pss_delta_kb" to run.pairedPssDeltaKilobytes,
            "final_pixel_digest_sha256" to run.digests.finalPixels,
            "entry_descriptor_digest_sha256" to run.digests.entryDescriptors,
            "projection_digest_sha256" to run.digests.projection,
            "raw_file_name" to run.artifact.fileName,
            "raw_byte_length" to run.artifact.byteLength,
            "raw_sha256" to run.artifact.sha256,
            "pss_individual_condition" to individualPssPass.status(),
            "steady_art_live_heap_condition" to heapPass.status(),
            "post_gc_churn_status" to P2AndroidMemoryProtocol.CHURN_STATUS,
            "peak_headroom_status" to UNEVALUATED,
            "candidate_retained_memory_status" to UNEVALUATED,
            "candidate_projection_status" to UNEVALUATED,
            "correctness_status" to PASS,
            "boundary" to RUN_BOUNDARY,
        )
    }

    private fun aggregateRow(
        input: P2AndroidMemoryAggregateInput,
        summary: P2AndroidMemoryAggregateSummary,
    ): P2AndroidMemoryCsvRow =
        P2AndroidMemoryCsv.row(
            "record_type" to "aggregate",
            "name" to P2AndroidMemoryProtocol.WORKLOAD_ID,
            *aggregateIdentityValues(input).toTypedArray(),
            *workloadValues().toTypedArray(),
            "memory_class_mib" to summary.memoryClassMib,
            "runtime_max_memory_bytes" to summary.runtimeMaxMemoryBytes,
            "median_paired_pss_delta_kb" to summary.medianPairedPssDeltaKilobytes,
            "maximum_paired_pss_delta_kb" to summary.maximumPairedPssDeltaKilobytes,
            "median_retained_java_heap_used_bytes" to summary.medianRetainedJavaHeapUsedBytes,
            "maximum_retained_java_heap_used_bytes" to summary.maximumRetainedJavaHeapUsedBytes,
            "pss_median_condition" to summary.pssMedianPass.status(),
            "pss_individual_condition" to summary.pssIndividualPass.status(),
            "steady_art_live_heap_condition" to summary.steadyArtLiveHeapPass.status(),
            "post_gc_churn_status" to P2AndroidMemoryProtocol.CHURN_STATUS,
            "peak_headroom_status" to UNEVALUATED,
            "candidate_retained_memory_status" to UNEVALUATED,
            "candidate_projection_status" to UNEVALUATED,
            "correctness_status" to PASS,
            "boundary" to AGGREGATE_BOUNDARY,
        )

    private fun summary(runs: List<P2AndroidMemoryAuditedRun>): P2AndroidMemoryAggregateSummary {
        val runtime = runs.first().runtime
        val pssDeltas = runs.map(P2AndroidMemoryAuditedRun::pairedPssDeltaKilobytes).sorted()
        val retainedHeap = runs.map { run -> run.runtime.retained.javaHeapUsedBytes }.sorted()
        val memoryClassKilobytes = runtime.memoryClassMib * KIBIBYTES_PER_MEBIBYTE.toLong()
        val medianPss = pssDeltas[MEDIAN_INDEX]
        return P2AndroidMemoryAggregateSummary(
            runtime = P2AndroidMemoryAggregateRuntime(runtime.memoryClassMib, runtime.runtimeMaxMemoryBytes),
            pss = P2AndroidMemoryAggregatePss(medianPss, pssDeltas.last()),
            heap = P2AndroidMemoryAggregateHeap(retainedHeap[MEDIAN_INDEX], retainedHeap.last()),
            conditions =
                P2AndroidMemoryAggregateConditions(
                    pssMedianPass = PSS_MEDIAN_MULTIPLIER * medianPss <= memoryClassKilobytes,
                    pssIndividualPass =
                        pssDeltas.all { delta ->
                            PSS_INDIVIDUAL_MULTIPLIER * delta <= PSS_LIMIT_MULTIPLIER * memoryClassKilobytes
                        },
                    steadyArtLiveHeapPass =
                        retainedHeap.all { used -> HEAP_MULTIPLIER * used <= runtime.runtimeMaxMemoryBytes },
                ),
        )
    }

    private fun aggregateIdentityValues(input: P2AndroidMemoryAggregateInput): List<Pair<String, Any?>> =
        listOf(
            "schema" to P2AndroidMemoryProtocol.AGGREGATE_SCHEMA,
            "evidence_class" to input.environment.evidenceClass,
            "physical_profile_id" to input.environment.profileId,
            "candidate_id" to input.identity.run.candidateId,
            "workload_id" to P2AndroidMemoryProtocol.WORKLOAD_ID,
            "batch_id" to input.identity.batchId,
            "source_commit" to input.identity.run.sourceCommit,
            "process_id" to input.identity.processId,
            "process_start_elapsed_realtime_ms" to input.identity.processStartElapsedRealtimeMillis,
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

    private fun validate(input: P2AndroidMemoryAggregateInput) {
        P2AndroidMemoryProtocol.validateEnvironment(input.environment)
        check(input.runs.size == P2AndroidMemoryProtocol.RUN_COUNT)
        check(input.runs.map { run -> run.process.runIndex } == P2AndroidMemoryProtocol.RUN_INDEX_RANGE.toList())
        check(
            input.runs
                .map { run -> run.artifact.fileName }
                .toSet()
                .size == input.runs.size,
        )
        check(input.runs.all { run -> run.artifact.byteLength > 0L && SHA256_PATTERN.matches(run.artifact.sha256) })
        val aggregateProcess = input.identity.processId to input.identity.processStartElapsedRealtimeMillis
        check(
            input.runs.none { run ->
                (run.process.processId to run.process.processStartElapsedRealtimeMillis) == aggregateProcess
            },
        ) {
            "Aggregate-only invocation must have a distinct process identity."
        }
    }

    private fun Boolean.status(): String = if (this) PASS else FAIL

    private const val PASS: String = "pass"
    private const val FAIL: String = "fail"
    private const val UNEVALUATED: String = "not_evaluated"
    private const val KIBIBYTES_PER_MEBIBYTE: Int = 1_024
    private const val MEDIAN_INDEX: Int = 2
    private const val PSS_MEDIAN_MULTIPLIER: Long = 2L
    private const val PSS_INDIVIDUAL_MULTIPLIER: Long = 5L
    private const val PSS_LIMIT_MULTIPLIER: Long = 3L
    private const val HEAP_MULTIPLIER: Long = 2L
    private const val RUN_BOUNDARY: String =
        "paired baseline and retained post-GC observations from one immutable instrumentation invocation"
    private const val AGGREGATE_BOUNDARY: String =
        "deterministic no-interpolation audit over exactly five independently checksummed raw invocations"
    private val SHA256_PATTERN: Regex = Regex("[0-9A-F]{64}")
}

internal data class P2AndroidMemoryAggregateRuntime(
    val memoryClassMib: Int,
    val runtimeMaxMemoryBytes: Long,
)

internal data class P2AndroidMemoryAggregatePss(
    val medianDeltaKilobytes: Long,
    val maximumDeltaKilobytes: Long,
)

internal data class P2AndroidMemoryAggregateHeap(
    val medianUsedBytes: Long,
    val maximumUsedBytes: Long,
)

internal data class P2AndroidMemoryAggregateConditions(
    val pssMedianPass: Boolean,
    val pssIndividualPass: Boolean,
    val steadyArtLiveHeapPass: Boolean,
)

internal data class P2AndroidMemoryAggregateSummary(
    val runtime: P2AndroidMemoryAggregateRuntime,
    val pss: P2AndroidMemoryAggregatePss,
    val heap: P2AndroidMemoryAggregateHeap,
    val conditions: P2AndroidMemoryAggregateConditions,
) {
    val memoryClassMib: Int
        get() = runtime.memoryClassMib
    val runtimeMaxMemoryBytes: Long
        get() = runtime.runtimeMaxMemoryBytes
    val medianPairedPssDeltaKilobytes: Long
        get() = pss.medianDeltaKilobytes
    val maximumPairedPssDeltaKilobytes: Long
        get() = pss.maximumDeltaKilobytes
    val medianRetainedJavaHeapUsedBytes: Long
        get() = heap.medianUsedBytes
    val maximumRetainedJavaHeapUsedBytes: Long
        get() = heap.maximumUsedBytes
    val pssMedianPass: Boolean
        get() = conditions.pssMedianPass
    val pssIndividualPass: Boolean
        get() = conditions.pssIndividualPass
    val steadyArtLiveHeapPass: Boolean
        get() = conditions.steadyArtLiveHeapPass
}
