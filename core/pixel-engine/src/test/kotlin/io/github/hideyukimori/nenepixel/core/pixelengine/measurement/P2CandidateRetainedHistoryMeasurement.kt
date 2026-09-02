package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

import kotlin.math.ceil

internal data class P2CandidateRetainedHistoryMeasurementDescriptor(
    val configuration: P2CandidateConfiguration,
    val workload: P2CandidateRetainedHistoryWorkload,
    val canvas: P2CanvasShape,
    val workloadIndex: Int,
    val executionOrder: Int,
) {
    val boundary: String =
        "test-only analytical entry/history wrapper construction and defensive entry-reference ownership; " +
            "snapshot, patch, inverse, fixture, replay, digest, storage analysis, and verification excluded"
}

internal data class P2CandidateRetainedHistoryStorageEvidence(
    val snapshot: P2CandidateStorageCounts,
    val forward: P2CandidatePatchStorageCounts,
    val inverseAdditional: P2CandidatePatchStorageCounts,
    val shared: P2CandidatePatchStorageCounts,
    val retainedUnion: P2CandidatePatchStorageCounts,
)

internal data class P2CandidateRetainedHistoryCorrectness(
    val entryChangeCountsDigest: String,
    val semanticDigest: String,
    val status: String,
)

internal data class P2CandidateRetainedHistoryMeasurementOutcome(
    val storage: P2CandidateRetainedHistoryStorageEvidence,
    val correctness: P2CandidateRetainedHistoryCorrectness,
)

internal data class P2CandidateRetainedHistoryMeasurementMetric(
    val descriptor: P2CandidateRetainedHistoryMeasurementDescriptor,
    val samples: P2RawSamples,
    val percentiles: P2CandidatePercentiles,
    val outcome: P2CandidateRetainedHistoryMeasurementOutcome,
)

internal object P2CandidateRetainedHistoryMeasurement {
    fun measure(allocationCounter: P2ThreadAllocationCounter): List<P2CandidateRetainedHistoryMeasurementMetric> {
        val metrics =
            P2CandidateRetainedHistoryMatrix.workloads.flatMapIndexed { workloadIndex, workload ->
                rotatedConfigurations(workloadIndex).mapIndexed { executionOrder, configuration ->
                    measureMetric(
                        allocationCounter,
                        P2CandidateRetainedHistoryMeasurementDescriptor(
                            configuration,
                            workload,
                            RETAINED_CANVAS,
                            workloadIndex,
                            executionOrder,
                        ),
                    )
                }
            }
        assertMetricMatrix(metrics)
        assertCrossConfigurationCorrectness(metrics)
        return metrics
    }

    private fun measureMetric(
        allocationCounter: P2ThreadAllocationCounter,
        descriptor: P2CandidateRetainedHistoryMeasurementDescriptor,
    ): P2CandidateRetainedHistoryMeasurementMetric {
        val fixture = P2CandidateRetainedHistoryMeasurementFixture.create(descriptor)
        var deterministicOutcome: P2CandidateRetainedHistoryMeasurementOutcome? = null
        repeat(WARMUP_ITERATIONS) {
            deterministicOutcome = deterministicOutcome.assertDeterministic(fixture.executeAndVerify())
        }
        val samples =
            sample(allocationCounter, fixture) { outcome ->
                deterministicOutcome = deterministicOutcome.assertDeterministic(outcome)
            }
        return P2CandidateRetainedHistoryMeasurementMetric(
            descriptor,
            samples,
            P2CandidatePercentiles(samples.latenciesNanos.percentiles(), samples.allocatedBytes.percentiles()),
            requireNotNull(deterministicOutcome),
        )
    }

    private fun sample(
        allocationCounter: P2ThreadAllocationCounter,
        fixture: P2CandidateRetainedHistoryMeasurementFixture,
        verify: (P2CandidateRetainedHistoryMeasurementOutcome) -> Unit,
    ): P2RawSamples {
        val latencies = LongArray(SAMPLE_COUNT)
        val allocations = LongArray(SAMPLE_COUNT)
        repeat(SAMPLE_COUNT) { index ->
            val allocationBefore = allocationCounter.currentThreadBytes()
            val startedAtNanos = System.nanoTime()
            val retained = fixture.execute()
            latencies[index] = System.nanoTime() - startedAtNanos
            allocations[index] = allocationCounter.currentThreadBytes() - allocationBefore
            verify(fixture.verify(retained))
        }
        return P2RawSamples(latencies, allocations)
    }

    private fun assertMetricMatrix(metrics: List<P2CandidateRetainedHistoryMeasurementMetric>) {
        check(metrics.size == METRIC_COUNT) { "Candidate retained-history matrix size changed." }
        check(
            metrics
                .map { metric -> metric.descriptor.workload to metric.descriptor.configuration }
                .toSet()
                .size == METRIC_COUNT,
        ) {
            "Candidate retained-history matrix contained a missing or duplicate configuration."
        }
        metrics.forEach { metric ->
            val descriptor = metric.descriptor
            val workload = descriptor.workload
            check(
                P2CandidateRetainedHistoryMatrix.isValidPair(
                    workload.historyEntries,
                    workload.totalRetainedChanges,
                    descriptor.canvas.pixelCount,
                ),
            ) {
                "Candidate retained-history matrix contained an invalid entry/change pair."
            }
            check(
                metric.samples.latenciesNanos.size == SAMPLE_COUNT,
            ) { "Retained-history latency sample count changed." }
            check(metric.samples.allocatedBytes.size == SAMPLE_COUNT) {
                "Retained-history allocation sample count changed."
            }
            assertStoragePolicy(metric)
        }
    }

    private fun assertStoragePolicy(metric: P2CandidateRetainedHistoryMeasurementMetric) {
        val workload = metric.descriptor.workload
        val storage = metric.outcome.storage
        if (workload.historyEntries == 0) {
            check(storage.forward == P2CandidatePatchStorageCounts.Empty)
            check(storage.inverseAdditional == P2CandidatePatchStorageCounts.Empty)
            check(storage.shared == P2CandidatePatchStorageCounts.Empty)
            check(storage.retainedUnion == P2CandidatePatchStorageCounts.Empty)
            return
        }
        when (metric.descriptor.configuration.patchLayout.inversePolicy) {
            P2CandidateInversePolicy.MaterializedRecords -> {
                check(storage.forward == storage.inverseAdditional)
                check(storage.shared == P2CandidatePatchStorageCounts.Empty)
                check(storage.retainedUnion == storage.forward + storage.inverseAdditional)
            }

            P2CandidateInversePolicy.SharedDirectionalView -> {
                check(storage.inverseAdditional == P2CandidatePatchStorageCounts.Empty)
                check(storage.shared == storage.forward)
                check(storage.retainedUnion == storage.forward)
            }
        }
    }

    private fun assertCrossConfigurationCorrectness(metrics: List<P2CandidateRetainedHistoryMeasurementMetric>) {
        metrics.groupBy { metric -> metric.descriptor.workload }.values.forEach { workloadMetrics ->
            val expected = workloadMetrics.first().outcome.correctness
            workloadMetrics.forEach { metric ->
                check(metric.outcome.correctness == expected) {
                    "Candidate retained-history semantics differed across configurations."
                }
            }
        }
    }

    private fun rotatedConfigurations(offset: Int): List<P2CandidateConfiguration> {
        val configurations = P2CandidateConfiguration.entries
        return configurations.indices.map { index -> configurations[(index + offset) % configurations.size] }
    }

    private fun P2CandidateRetainedHistoryMeasurementOutcome?.assertDeterministic(
        actual: P2CandidateRetainedHistoryMeasurementOutcome,
    ): P2CandidateRetainedHistoryMeasurementOutcome {
        if (this != null) check(this == actual) { "Candidate retained-history outcome was not deterministic." }
        return actual
    }

    private fun LongArray.percentiles(): P2Percentiles =
        P2Percentiles(
            median = percentile(MEDIAN_PERCENTILE),
            p95 = percentile(P95_PERCENTILE),
            p99 = percentile(P99_PERCENTILE),
        )

    private fun LongArray.percentile(percentile: Double): Long {
        val sorted = sortedArray()
        val index = ceil(sorted.size * percentile).toInt().coerceIn(1, sorted.size) - 1
        return sorted[index]
    }

    const val WARMUP_ITERATIONS: Int = 5
    const val SAMPLE_COUNT: Int = 10
    const val METRIC_COUNT: Int = 90
    const val RAW_SAMPLE_COUNT: Int = 900
    private const val MEDIAN_PERCENTILE: Double = 0.50
    private const val P95_PERCENTILE: Double = 0.95
    private const val P99_PERCENTILE: Double = 0.99
    private val RETAINED_CANVAS: P2CanvasShape = P2CanvasShape(256, 256)
}
