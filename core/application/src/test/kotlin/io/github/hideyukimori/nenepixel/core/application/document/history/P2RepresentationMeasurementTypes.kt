package io.github.hideyukimori.nenepixel.core.application.document.history

internal data class P2MeasurementDescriptor(
    val name: String,
    val workload: P2WorkloadShape,
    val sampling: P2SamplingPlan,
    val boundary: String,
) {
    val pixelCount: Long
        get() = workload.canvas.pixelCount

    val totalRetainedChanges: Long
        get() = workload.historyEntries.toLong() * workload.changeCount.toLong()
}

internal data class P2WorkloadShape(
    val canvas: P2CanvasShape,
    val pathPositions: Int,
    val changeCount: Int,
    val historyEntries: Int,
)

internal data class P2CanvasShape(
    val width: Int,
    val height: Int,
) {
    val pixelCount: Long
        get() = width.toLong() * height.toLong()
}

internal data class P2SamplingPlan(
    val warmupIterations: Int,
    val sampleCount: Int,
)

internal data class P2MeasurementMetric(
    val descriptor: P2MeasurementDescriptor,
    val samples: P2RawSamples,
    val latency: P2Percentiles,
    val allocation: P2Percentiles,
)

internal data class P2RawSamples(
    val latenciesNanos: LongArray,
    val allocatedBytes: LongArray,
)

internal data class P2Percentiles(
    val median: Long,
    val p95: Long,
    val p99: Long,
)

internal sealed interface P2AnalysisRow {
    val descriptor: P2AnalysisDescriptor

    data class RetainedStructure(
        override val descriptor: P2AnalysisDescriptor,
        val counts: P2RetainedStructureCounts,
    ) : P2AnalysisRow

    data class ExcludedCandidate(
        override val descriptor: P2AnalysisDescriptor,
        val reason: P2AnalysisExclusion,
    ) : P2AnalysisRow
}

internal data class P2AnalysisDescriptor(
    val name: String,
    val canvas: P2CanvasShape,
    val retained: P2RetainedWorkload,
    val boundary: String,
)

internal data class P2RetainedWorkload(
    val changeCount: Long,
    val historyEntries: Int,
) {
    val totalChanges: Long
        get() = changeCount * historyEntries.toLong()
}

internal data class P2RetainedStructureCounts(
    val snapshotPixelReferenceSlots: Long,
    val forwardChangeRecords: Long,
    val inverseChangeRecords: Long,
)

internal enum class P2AnalysisExclusion {
    DenseChangesExceedWorkerBudget,
    PixelCountExceedsListIndexability,
    RetainedChangesExceedWorkerBudget,
}

internal data class P2MeasuredOperation<T : Any, K : Any>(
    val execute: () -> T,
    val verify: (T) -> Unit,
    val deterministicKey: (T) -> K,
)
