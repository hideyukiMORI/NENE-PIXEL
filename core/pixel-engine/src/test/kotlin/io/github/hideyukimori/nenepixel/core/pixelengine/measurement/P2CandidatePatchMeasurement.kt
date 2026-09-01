package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

import kotlin.math.ceil

internal enum class P2CandidatePatchOperationKind(
    val csvName: String,
) {
    CreateShuffled("patch_create_shuffled"),
    CreateInverse("patch_inverse_create"),
    ApplyForward("patch_apply_forward"),
    ApplyInverse("patch_apply_inverse"),
    RoundTrip("patch_round_trip"),
    ApplyLateConflict("patch_apply_late_conflict"),
}

internal data class P2CandidatePatchMeasurementProtocol(
    val boundary: String,
    val executionOrder: Int,
    val inputOrder: String,
)

internal data class P2CandidatePatchMeasurementDescriptor(
    val configuration: P2CandidateConfiguration,
    val operation: P2CandidatePatchOperationKind,
    val canvas: P2CanvasShape,
    val protocol: P2CandidatePatchMeasurementProtocol,
)

internal data class P2CandidatePatchRevisionEvidence(
    val before: Long,
    val after: Long,
    val restored: Long,
)

internal data class P2CandidatePatchLifecycleDigests(
    val before: String,
    val applied: String,
    val restored: String,
)

internal data class P2CandidatePatchLifecycleEvidence(
    val revisions: P2CandidatePatchRevisionEvidence,
    val digests: P2CandidatePatchLifecycleDigests,
)

internal data class P2CandidatePatchOperationEvidence(
    val inputRevision: Long,
    val outputRevision: Long,
    val inputPixelDigest: String,
    val outputPixelDigest: String,
)

internal data class P2CandidatePatchUnaffectedEvidence(
    val inputDigest: String,
    val outputDigest: String,
)

internal data class P2CandidatePatchStateEvidence(
    val lifecycle: P2CandidatePatchLifecycleEvidence,
    val operation: P2CandidatePatchOperationEvidence,
    val unaffected: P2CandidatePatchUnaffectedEvidence,
    val affectedRegion: P2CandidateAffectedRegion,
)

internal data class P2CandidatePatchCorrectness(
    val canonicalOrderDigest: String,
    val forwardPatchDigest: String,
    val inversePatchDigest: String,
    val status: String,
)

internal data class P2CandidatePatchResultEvidence(
    val resultKind: String,
    val rejectionKind: String,
    val conflictPosition: Int?,
)

internal data class P2CandidatePatchStorageEvidence(
    val snapshot: P2CandidateStorageCounts,
    val patch: P2CandidatePatchPairStorage,
)

internal data class P2CandidatePatchMeasurementOutcome(
    val storage: P2CandidatePatchStorageEvidence,
    val state: P2CandidatePatchStateEvidence,
    val correctness: P2CandidatePatchCorrectness,
    val result: P2CandidatePatchResultEvidence,
)

internal data class P2CandidatePatchMeasurementMetric(
    val descriptor: P2CandidatePatchMeasurementDescriptor,
    val samples: P2RawSamples,
    val percentiles: P2CandidatePercentiles,
    val outcome: P2CandidatePatchMeasurementOutcome,
)

internal object P2CandidatePatchMeasurement {
    fun measure(allocationCounter: P2ThreadAllocationCounter): List<P2CandidatePatchMeasurementMetric> {
        val metrics =
            P2CandidatePatchOperationKind.entries.flatMapIndexed { operationIndex, operation ->
                rotatedConfigurations(operationIndex).mapIndexed { executionOrder, configuration ->
                    measureMetric(
                        allocationCounter,
                        descriptor(configuration, operation, executionOrder),
                    )
                }
            }
        assertMetricMatrix(metrics)
        assertCrossConfigurationCorrectness(metrics)
        return metrics
    }

    private fun measureMetric(
        allocationCounter: P2ThreadAllocationCounter,
        descriptor: P2CandidatePatchMeasurementDescriptor,
    ): P2CandidatePatchMeasurementMetric {
        val fixture = P2CandidatePatchMeasurementFixture.create(descriptor)
        var deterministicOutcome: P2CandidatePatchMeasurementOutcome? = null
        repeat(WARMUP_ITERATIONS) {
            deterministicOutcome = deterministicOutcome.assertDeterministic(fixture.executeAndVerify())
        }
        val samples =
            sample(allocationCounter, fixture) { outcome ->
                deterministicOutcome = deterministicOutcome.assertDeterministic(outcome)
            }
        return P2CandidatePatchMeasurementMetric(
            descriptor,
            samples,
            P2CandidatePercentiles(samples.latenciesNanos.percentiles(), samples.allocatedBytes.percentiles()),
            requireNotNull(deterministicOutcome),
        )
    }

    private fun sample(
        allocationCounter: P2ThreadAllocationCounter,
        fixture: P2CandidatePatchMeasurementFixture,
        verify: (P2CandidatePatchMeasurementOutcome) -> Unit,
    ): P2RawSamples {
        val latencies = LongArray(SAMPLE_COUNT)
        val allocations = LongArray(SAMPLE_COUNT)
        repeat(SAMPLE_COUNT) { index ->
            val allocationBefore = allocationCounter.currentThreadBytes()
            val startedAtNanos = System.nanoTime()
            val execution = fixture.execute()
            latencies[index] = System.nanoTime() - startedAtNanos
            allocations[index] = allocationCounter.currentThreadBytes() - allocationBefore
            verify(fixture.verify(execution))
        }
        return P2RawSamples(latencies, allocations)
    }

    private fun descriptor(
        configuration: P2CandidateConfiguration,
        operation: P2CandidatePatchOperationKind,
        executionOrder: Int,
    ): P2CandidatePatchMeasurementDescriptor =
        P2CandidatePatchMeasurementDescriptor(
            configuration = configuration,
            operation = operation,
            canvas = PATCH_CANVAS,
            protocol =
                P2CandidatePatchMeasurementProtocol(
                    boundary = operation.boundary(),
                    executionOrder = executionOrder,
                    inputOrder = "reverse_row_major",
                ),
        )

    private fun rotatedConfigurations(offset: Int): List<P2CandidateConfiguration> {
        val configurations = P2CandidateConfiguration.entries
        return configurations.indices.map { index -> configurations[(index + offset) % configurations.size] }
    }

    private fun assertCrossConfigurationCorrectness(metrics: List<P2CandidatePatchMeasurementMetric>) {
        metrics.groupBy { metric -> metric.descriptor.operation }.values.forEach { operationMetrics ->
            val expectedState = operationMetrics.first().outcome.state
            val expectedCorrectness = operationMetrics.first().outcome.correctness
            val expectedResult = operationMetrics.first().outcome.result
            operationMetrics.forEach { metric ->
                check(metric.outcome.state == expectedState) { "Candidate patch state differed across configurations." }
                check(metric.outcome.correctness == expectedCorrectness) {
                    "Candidate patch semantics differed across configurations."
                }
                check(
                    metric.outcome.result == expectedResult,
                ) { "Candidate patch result differed across configurations." }
            }
        }
    }

    private fun assertMetricMatrix(metrics: List<P2CandidatePatchMeasurementMetric>) {
        val expectedCount = P2CandidatePatchOperationKind.entries.size * P2CandidateConfiguration.entries.size
        check(metrics.size == expectedCount) { "Candidate patch matrix size changed." }
        check(
            metrics.map { metric -> metric.descriptor.operation to metric.descriptor.configuration }.toSet().size ==
                expectedCount,
        ) {
            "Candidate patch matrix contained a missing or duplicate configuration."
        }
        metrics.forEach { metric ->
            val expected = metric.descriptor.operation.expectedResult()
            check(metric.outcome.result.resultKind == expected.first) { "Candidate patch result kind changed." }
            check(metric.outcome.result.rejectionKind == expected.second) { "Candidate patch rejection kind changed." }
            val operation = metric.outcome.state.operation
            val stateUnchanged =
                operation.inputRevision == operation.outputRevision &&
                    operation.inputPixelDigest == operation.outputPixelDigest
            check(stateUnchanged == metric.descriptor.operation.expectsUnchangedState()) {
                "Candidate patch operation state boundary changed."
            }
        }
    }

    private fun P2CandidatePatchOperationKind.expectedResult(): Pair<String, String> =
        when (this) {
            P2CandidatePatchOperationKind.CreateShuffled -> "Created" to ""

            P2CandidatePatchOperationKind.CreateInverse -> "Inverted" to ""

            P2CandidatePatchOperationKind.ApplyForward,
            P2CandidatePatchOperationKind.ApplyInverse,
            -> "Applied" to ""

            P2CandidatePatchOperationKind.RoundTrip -> "RoundTripped" to ""

            P2CandidatePatchOperationKind.ApplyLateConflict -> "Rejected" to "BeforeValueMismatch"
        }

    private fun P2CandidatePatchOperationKind.expectsUnchangedState(): Boolean =
        this != P2CandidatePatchOperationKind.ApplyForward && this != P2CandidatePatchOperationKind.ApplyInverse

    private fun P2CandidatePatchMeasurementOutcome?.assertDeterministic(
        actual: P2CandidatePatchMeasurementOutcome,
    ): P2CandidatePatchMeasurementOutcome {
        if (this != null) check(this == actual) { "Candidate patch measurement outcome was not deterministic." }
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

    private fun P2CandidatePatchOperationKind.boundary(): String =
        when (this) {
            P2CandidatePatchOperationKind.CreateShuffled -> {
                "shared canonicalization, candidate-native materialization, and defensive ownership"
            }

            P2CandidatePatchOperationKind.CreateInverse -> {
                "candidate-native inverse creation"
            }

            P2CandidatePatchOperationKind.ApplyForward -> {
                "candidate-native validation and forward snapshot apply"
            }

            P2CandidatePatchOperationKind.ApplyInverse -> {
                "candidate-native validation and inverse snapshot apply"
            }

            P2CandidatePatchOperationKind.RoundTrip -> {
                "forward apply, inverse creation, and inverse apply"
            }

            P2CandidatePatchOperationKind.ApplyLateConflict -> {
                "typed final-record conflict validation"
            }
        } + "; semantic input generation and full verification excluded"

    private const val WARMUP_ITERATIONS: Int = 5
    private const val SAMPLE_COUNT: Int = 10
    private const val MEDIAN_PERCENTILE: Double = 0.50
    private const val P95_PERCENTILE: Double = 0.95
    private const val P99_PERCENTILE: Double = 0.99
    private val PATCH_CANVAS: P2CanvasShape = P2CanvasShape(256, 256)
}
