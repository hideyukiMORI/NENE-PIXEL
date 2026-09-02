package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import kotlin.math.ceil

internal enum class P2CandidateRawPathOperationKind(
    val csvName: String,
) {
    DuplicateChanged("raw_duplicate_changed"),
    ReferenceClearChanged("raw_reference_clear_changed"),
    ReferenceClearNoOp("raw_reference_clear_noop"),
    SameColorNoOp("raw_same_color_noop"),
}

internal data class P2CandidateRawPathMeasurementProtocol(
    val boundary: String,
    val executionOrder: Int,
    val inputOrder: String,
)

internal data class P2CandidateRawPathMeasurementDescriptor(
    val configuration: P2CandidateConfiguration,
    val operation: P2CandidateRawPathOperationKind,
    val canvas: P2CanvasShape,
    val pathPositions: Int,
    val uniquePathPositions: Int,
    val duplicatePathPositions: Int,
    val unchangedUniquePositions: Int,
    val changeCount: Int,
    val contentKind: String,
    val protocol: P2CandidateRawPathMeasurementProtocol,
)

internal data class P2CandidateRawPathStateEvidence(
    val lifecycle: P2CandidatePatchLifecycleEvidence,
    val operation: P2CandidatePatchOperationEvidence,
    val unaffected: P2CandidatePatchUnaffectedEvidence,
    val unaffectedPixelCount: Int,
    val affectedRegion: P2CandidateAffectedRegion?,
)

internal data class P2CandidateRawPathCorrectness(
    val rawInputDigest: String,
    val canonicalChangeDigest: String,
    val canonicalOrderDigest: String,
    val forwardPatchDigest: String,
    val inversePatchDigest: String,
    val status: String,
)

internal data class P2CandidateRawPathMeasurementOutcome(
    val storage: P2CandidatePatchStorageEvidence,
    val state: P2CandidateRawPathStateEvidence,
    val correctness: P2CandidateRawPathCorrectness,
    val result: P2CandidatePatchResultEvidence,
)

internal data class P2CandidateRawPathMeasurementMetric(
    val descriptor: P2CandidateRawPathMeasurementDescriptor,
    val samples: P2RawSamples,
    val percentiles: P2CandidatePercentiles,
    val outcome: P2CandidateRawPathMeasurementOutcome,
)

internal object P2CandidateRawPathMeasurement {
    fun measure(allocationCounter: P2ThreadAllocationCounter): List<P2CandidateRawPathMeasurementMetric> {
        val metrics =
            P2CandidateRawPathOperationKind.entries.flatMapIndexed { operationIndex, operation ->
                rotatedConfigurations(operationIndex).mapIndexed { executionOrder, configuration ->
                    measureMetric(allocationCounter, descriptor(configuration, operation, executionOrder))
                }
            }
        assertMetricMatrix(metrics)
        assertCrossConfigurationCorrectness(metrics)
        return metrics
    }

    private fun measureMetric(
        allocationCounter: P2ThreadAllocationCounter,
        descriptor: P2CandidateRawPathMeasurementDescriptor,
    ): P2CandidateRawPathMeasurementMetric {
        val fixture = P2CandidateRawPathMeasurementFixture.create(descriptor)
        var deterministicOutcome: P2CandidateRawPathMeasurementOutcome? = null
        repeat(WARMUP_ITERATIONS) {
            deterministicOutcome = deterministicOutcome.assertDeterministic(fixture.executeAndVerify())
        }
        val samples =
            sample(allocationCounter, fixture) { outcome ->
                deterministicOutcome = deterministicOutcome.assertDeterministic(outcome)
            }
        return P2CandidateRawPathMeasurementMetric(
            descriptor = descriptor,
            samples = samples,
            percentiles =
                P2CandidatePercentiles(
                    samples.latenciesNanos.percentiles(),
                    samples.allocatedBytes.percentiles(),
                ),
            outcome = requireNotNull(deterministicOutcome),
        )
    }

    private fun sample(
        allocationCounter: P2ThreadAllocationCounter,
        fixture: P2CandidateRawPathMeasurementFixture,
        verify: (P2CandidateRawPathMeasurementOutcome) -> Unit,
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
        operation: P2CandidateRawPathOperationKind,
        executionOrder: Int,
    ): P2CandidateRawPathMeasurementDescriptor {
        val pixelCount = RAW_CANVAS.pixelCount.toInt()
        val duplicateCount = if (operation == P2CandidateRawPathOperationKind.DuplicateChanged) pixelCount else 0
        val noOp = operation.isNoOp()
        return P2CandidateRawPathMeasurementDescriptor(
            configuration = configuration,
            operation = operation,
            canvas = RAW_CANVAS,
            pathPositions = pixelCount + duplicateCount,
            uniquePathPositions = pixelCount,
            duplicatePathPositions = duplicateCount,
            unchangedUniquePositions = if (noOp) pixelCount else 0,
            changeCount = if (noOp) 0 else pixelCount,
            contentKind = operation.contentKind(),
            protocol =
                P2CandidateRawPathMeasurementProtocol(
                    boundary = RAW_BOUNDARY,
                    executionOrder = executionOrder,
                    inputOrder = operation.inputOrder(),
                ),
        )
    }

    private fun rotatedConfigurations(offset: Int): List<P2CandidateConfiguration> {
        val configurations = P2CandidateConfiguration.entries
        return configurations.indices.map { index -> configurations[(index + offset) % configurations.size] }
    }

    private fun assertMetricMatrix(metrics: List<P2CandidateRawPathMeasurementMetric>) {
        val expectedCount = P2CandidateRawPathOperationKind.entries.size * P2CandidateConfiguration.entries.size
        check(metrics.size == expectedCount) { "Candidate raw-path matrix size changed." }
        check(
            metrics.map { metric -> metric.descriptor.operation to metric.descriptor.configuration }.toSet().size ==
                expectedCount,
        ) {
            "Candidate raw-path matrix contained a missing or duplicate configuration."
        }
        metrics.forEach { metric ->
            check(metric.outcome.result.resultKind == metric.descriptor.operation.expectedResultKind()) {
                "Candidate raw-path result kind changed."
            }
            check(
                metric.outcome.result.rejectionKind
                    .isEmpty(),
            ) { "Candidate raw-path workload was rejected." }
            check(metric.outcome.state.operation.inputRevision == metric.outcome.state.operation.outputRevision) {
                "Candidate raw-path creation changed the source revision."
            }
            check(metric.outcome.state.operation.inputPixelDigest == metric.outcome.state.operation.outputPixelDigest) {
                "Candidate raw-path creation changed source pixels."
            }
        }
    }

    private fun assertCrossConfigurationCorrectness(metrics: List<P2CandidateRawPathMeasurementMetric>) {
        metrics.groupBy { metric -> metric.descriptor.operation }.values.forEach { operationMetrics ->
            val expectedState = operationMetrics.first().outcome.state
            val expectedCorrectness = operationMetrics.first().outcome.correctness
            val expectedResult = operationMetrics.first().outcome.result
            operationMetrics.forEach { metric ->
                check(metric.outcome.state == expectedState) {
                    "Candidate raw-path state differed across configurations."
                }
                check(metric.outcome.correctness == expectedCorrectness) {
                    "Candidate raw-path semantics differed across configurations."
                }
                check(metric.outcome.result == expectedResult) {
                    "Candidate raw-path result differed across configurations."
                }
            }
        }
    }

    private fun P2CandidateRawPathMeasurementOutcome?.assertDeterministic(
        actual: P2CandidateRawPathMeasurementOutcome,
    ): P2CandidateRawPathMeasurementOutcome {
        if (this != null) check(this == actual) { "Candidate raw-path outcome was not deterministic." }
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

    private const val WARMUP_ITERATIONS: Int = 5
    private const val SAMPLE_COUNT: Int = 10
    private const val MEDIAN_PERCENTILE: Double = 0.50
    private const val P95_PERCENTILE: Double = 0.95
    private const val P99_PERCENTILE: Double = 0.99
    private const val RAW_BOUNDARY: String =
        "raw position scan, first-occurrence duplicate collapse, source-color filter, canonical change collection, " +
            "candidate-native patch materialization, defensive ownership, and typed result return; " +
            "fixture generation, apply/inverse, history, and verification excluded"
    private val RAW_CANVAS: P2CanvasShape = P2CanvasShape(256, 256)
}

private class P2CandidateRawPathMeasurementFixture(
    private val descriptor: P2CandidateRawPathMeasurementDescriptor,
    private val source: P2CandidateSnapshot,
    private val rawPositions: IntArray,
    private val target: PixelColor,
    private val sourcePacked: Int,
    private val targetPacked: Int,
    private val sourceDigest: String,
    private val rawInputDigest: String,
) {
    fun execute(): P2CandidateRawPathResult =
        P2CandidateRawPathPatchFactory.create(
            descriptor.configuration,
            source,
            rawPositions,
            target,
        )

    fun verify(execution: P2CandidateRawPathResult): P2CandidateRawPathMeasurementOutcome {
        check(P2CandidateDigest.rawInput(source, rawPositions, target) == rawInputDigest) {
            "Candidate raw-path input changed."
        }
        check(source.revision == SOURCE_REVISION) { "Candidate raw-path source revision changed." }
        check(P2CandidateDigest.pixels(source) == sourceDigest) { "Candidate raw-path source pixels changed." }
        return when (execution) {
            is P2CandidateRawPathResult.Rasterized -> {
                verifyRasterized(execution.patch)
            }

            P2CandidateRawPathResult.NoChanges -> {
                verifyNoChanges()
            }

            is P2CandidateRawPathResult.Rejected -> {
                error("Candidate raw-path workload was rejected: ${execution.rejection}")
            }
        }
    }

    fun executeAndVerify(): P2CandidateRawPathMeasurementOutcome = verify(execute())

    private fun verifyRasterized(patch: P2CandidatePatch): P2CandidateRawPathMeasurementOutcome {
        check(descriptor.changeCount > 0) { "Candidate raw-path workload unexpectedly rasterized a no-op." }
        assertPatch(patch)
        val inverse = patch.inverse()
        val applied = source.apply(patch).requiredApplication().snapshot
        val restored = applied.apply(inverse).requiredApplication().snapshot
        assertCandidatePixels(applied, IntArray(descriptor.canvas.pixelCount.toInt()) { targetPacked })
        assertCandidatePixels(restored, IntArray(descriptor.canvas.pixelCount.toInt()) { sourcePacked })
        check(restored == source) { "Candidate raw-path inverse did not restore the source snapshot." }

        val appliedDigest = P2CandidateDigest.pixels(applied)
        val unaffectedDigest = P2CandidateDigest.unaffectedPixels(source, patch)
        return outcome(
            patchStorage = patch.pairStorage(inverse),
            lifecycle = lifecycle(applied.revision, appliedDigest, source.revision, sourceDigest),
            unaffected = P2CandidatePatchUnaffectedEvidence(0, unaffectedDigest, unaffectedDigest),
            unaffectedPixelCount = 0,
            affectedRegion = patch.affectedRegion,
            correctness =
                P2CandidateRawPathCorrectness(
                    rawInputDigest = rawInputDigest,
                    canonicalChangeDigest = P2CandidateDigest.canonicalChanges(patch),
                    canonicalOrderDigest = P2CandidateDigest.canonicalOrder(patch),
                    forwardPatchDigest = P2CandidateDigest.patch(patch),
                    inversePatchDigest = P2CandidateDigest.patch(inverse),
                    status = "pass",
                ),
            resultKind = "Rasterized",
        )
    }

    private fun verifyNoChanges(): P2CandidateRawPathMeasurementOutcome {
        check(descriptor.changeCount == 0) { "Candidate raw-path workload unexpectedly returned no changes." }
        val unaffectedDigest = P2CandidateDigest.allIndexedPixels(source)
        return outcome(
            patchStorage = EMPTY_PATCH_PAIR_STORAGE,
            lifecycle = lifecycle(source.revision, sourceDigest, source.revision, sourceDigest),
            unaffected =
                P2CandidatePatchUnaffectedEvidence(
                    descriptor.canvas.pixelCount.toInt(),
                    unaffectedDigest,
                    unaffectedDigest,
                ),
            unaffectedPixelCount = descriptor.canvas.pixelCount.toInt(),
            affectedRegion = null,
            correctness =
                P2CandidateRawPathCorrectness(
                    rawInputDigest = rawInputDigest,
                    canonicalChangeDigest = P2CandidateDigest.canonicalChanges(null),
                    canonicalOrderDigest = "",
                    forwardPatchDigest = "",
                    inversePatchDigest = "",
                    status = "pass",
                ),
            resultKind = "NoChanges",
        )
    }

    private fun outcome(
        patchStorage: P2CandidatePatchPairStorage,
        lifecycle: P2CandidatePatchLifecycleEvidence,
        unaffected: P2CandidatePatchUnaffectedEvidence,
        unaffectedPixelCount: Int,
        affectedRegion: P2CandidateAffectedRegion?,
        correctness: P2CandidateRawPathCorrectness,
        resultKind: String,
    ): P2CandidateRawPathMeasurementOutcome =
        P2CandidateRawPathMeasurementOutcome(
            storage = P2CandidatePatchStorageEvidence(source.storage, patchStorage),
            state =
                P2CandidateRawPathStateEvidence(
                    lifecycle = lifecycle,
                    operation =
                        P2CandidatePatchOperationEvidence(
                            inputRevision = source.revision,
                            outputRevision = source.revision,
                            inputPixelDigest = sourceDigest,
                            outputPixelDigest = sourceDigest,
                        ),
                    unaffected = unaffected,
                    unaffectedPixelCount = unaffectedPixelCount,
                    affectedRegion = affectedRegion,
                ),
            correctness = correctness,
            result = P2CandidatePatchResultEvidence(resultKind, "", null),
        )

    private fun lifecycle(
        afterRevision: Long,
        afterDigest: String,
        restoredRevision: Long,
        restoredDigest: String,
    ): P2CandidatePatchLifecycleEvidence =
        P2CandidatePatchLifecycleEvidence(
            revisions = P2CandidatePatchRevisionEvidence(source.revision, afterRevision, restoredRevision),
            digests = P2CandidatePatchLifecycleDigests(sourceDigest, afterDigest, restoredDigest),
        )

    private fun assertPatch(patch: P2CandidatePatch) {
        check(patch.configuration == descriptor.configuration) { "Candidate raw-path configuration changed." }
        check(patch.shape == descriptor.canvas) { "Candidate raw-path shape changed." }
        check(patch.direction == P2CandidatePatchDirection.Forward) { "Candidate raw-path direction changed." }
        check(patch.revisions == P2CandidateRevisionTransition(SOURCE_REVISION, SOURCE_REVISION + 1L)) {
            "Candidate raw-path revisions changed."
        }
        check(patch.changeCount == descriptor.changeCount) { "Candidate raw-path change count changed." }
        repeat(patch.changeCount) { index ->
            check(patch.positionAt(index) == index) { "Candidate raw-path canonical order changed at $index." }
            check(patch.beforeAt(index) == sourcePacked) { "Candidate raw-path before value changed at $index." }
            check(patch.afterAt(index) == targetPacked) { "Candidate raw-path after value changed at $index." }
        }
        check(
            patch.affectedRegion == P2CandidateAffectedRegion(0, 0, descriptor.canvas.width, descriptor.canvas.height),
        ) {
            "Candidate raw-path affected region changed."
        }
    }

    companion object {
        fun create(descriptor: P2CandidateRawPathMeasurementDescriptor): P2CandidateRawPathMeasurementFixture {
            val sourcePacked = descriptor.operation.sourcePacked()
            val targetPacked = descriptor.operation.targetPacked()
            val sourcePixels = IntArray(descriptor.canvas.pixelCount.toInt()) { sourcePacked }
            val source =
                descriptor.configuration.createSnapshot(
                    descriptor.canvas,
                    SOURCE_REVISION,
                    sourcePixels,
                )
            val rawPositions = descriptor.operation.rawPositions(descriptor.canvas.pixelCount.toInt())
            val target = P2PackedRgba8888.unpack(targetPacked)
            return P2CandidateRawPathMeasurementFixture(
                descriptor = descriptor,
                source = source,
                rawPositions = rawPositions,
                target = target,
                sourcePacked = sourcePacked,
                targetPacked = targetPacked,
                sourceDigest = P2CandidateDigest.pixels(source),
                rawInputDigest = P2CandidateDigest.rawInput(source, rawPositions, target),
            )
        }
    }
}

private fun P2CandidateRawPathOperationKind.isNoOp(): Boolean =
    this == P2CandidateRawPathOperationKind.ReferenceClearNoOp ||
        this == P2CandidateRawPathOperationKind.SameColorNoOp

private fun P2CandidateRawPathOperationKind.sourcePacked(): Int =
    when (this) {
        P2CandidateRawPathOperationKind.DuplicateChanged,
        P2CandidateRawPathOperationKind.ReferenceClearNoOp,
        -> OPAQUE_BLACK

        P2CandidateRawPathOperationKind.ReferenceClearChanged,
        P2CandidateRawPathOperationKind.SameColorNoOp,
        -> OPAQUE_RED
    }

private fun P2CandidateRawPathOperationKind.targetPacked(): Int =
    when (this) {
        P2CandidateRawPathOperationKind.DuplicateChanged,
        P2CandidateRawPathOperationKind.SameColorNoOp,
        -> OPAQUE_RED

        P2CandidateRawPathOperationKind.ReferenceClearChanged,
        P2CandidateRawPathOperationKind.ReferenceClearNoOp,
        -> OPAQUE_BLACK
    }

private fun P2CandidateRawPathOperationKind.rawPositions(pixelCount: Int): IntArray =
    if (this == P2CandidateRawPathOperationKind.DuplicateChanged) {
        IntArray(pixelCount * 2) { index -> index / 2 }
    } else {
        IntArray(pixelCount) { index -> index }
    }

private fun P2CandidateRawPathOperationKind.inputOrder(): String =
    if (this == P2CandidateRawPathOperationKind.DuplicateChanged) "paired_row_major" else "row_major"

private fun P2CandidateRawPathOperationKind.contentKind(): String =
    when (this) {
        P2CandidateRawPathOperationKind.DuplicateChanged -> "uniform_opaque_black_to_opaque_red"
        P2CandidateRawPathOperationKind.ReferenceClearChanged -> "uniform_opaque_red_to_reference_black"
        P2CandidateRawPathOperationKind.ReferenceClearNoOp -> "uniform_reference_black_to_same_black"
        P2CandidateRawPathOperationKind.SameColorNoOp -> "uniform_opaque_red_to_same_red"
    }

private fun P2CandidateRawPathOperationKind.expectedResultKind(): String = if (isNoOp()) "NoChanges" else "Rasterized"

private val EMPTY_PATCH_PAIR_STORAGE: P2CandidatePatchPairStorage =
    P2CandidatePatchPairStorage(
        forward = P2CandidatePatchStorageCounts.Empty,
        inverseAdditional = P2CandidatePatchStorageCounts.Empty,
        shared = P2CandidatePatchStorageCounts.Empty,
        retainedUnion = P2CandidatePatchStorageCounts.Empty,
    )

private const val SOURCE_REVISION: Long = 0L
private const val OPAQUE_BLACK: Int = 0x000000ff
private const val OPAQUE_RED: Int = -0x00ffff01
