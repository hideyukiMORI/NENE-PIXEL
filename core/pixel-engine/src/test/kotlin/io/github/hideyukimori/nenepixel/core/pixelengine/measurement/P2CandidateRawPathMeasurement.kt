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
    val repeatFactor: Int,
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
        val descriptors = descriptors()
        P2CandidateRawPathMeasurementMatrix.validate(descriptors)
        val metrics = descriptors.map { descriptor -> measureMetric(allocationCounter, descriptor) }
        assertMetricMatrix(metrics)
        assertCrossConfigurationCorrectness(metrics)
        assertCrossFactorCorrectness(metrics)
        return metrics
    }

    internal fun descriptors(): List<P2CandidateRawPathMeasurementDescriptor> =
        P2CandidateRawPathMeasurementMatrix.works.flatMap { work ->
            rotatedConfigurations(work.rotationIndex).mapIndexed { executionOrder, configuration ->
                descriptor(work, configuration, executionOrder)
            }
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
        work: P2CandidateRawPathMeasurementWork,
        configuration: P2CandidateConfiguration,
        executionOrder: Int,
    ): P2CandidateRawPathMeasurementDescriptor {
        val noOp = work.operation.isNoOp()
        return P2CandidateRawPathMeasurementDescriptor(
            configuration = configuration,
            operation = work.operation,
            canvas = work.canvas,
            repeatFactor = work.repeatFactor,
            pathPositions = work.pathPositions,
            uniquePathPositions = work.pixelCount,
            duplicatePathPositions = work.duplicatePathPositions,
            unchangedUniquePositions = if (noOp) work.pixelCount else 0,
            changeCount = work.changeCount,
            contentKind = work.operation.contentKind(),
            protocol =
                P2CandidateRawPathMeasurementProtocol(
                    boundary = RAW_BOUNDARY,
                    executionOrder = executionOrder,
                    inputOrder = work.inputOrder(),
                ),
        )
    }

    private fun rotatedConfigurations(offset: Int): List<P2CandidateConfiguration> {
        val configurations = P2CandidateConfiguration.entries
        return configurations.indices.map { index -> configurations[(index + offset) % configurations.size] }
    }

    private fun assertMetricMatrix(metrics: List<P2CandidateRawPathMeasurementMetric>) {
        val expectedCount = P2CandidateRawPathMeasurementMatrix.METRIC_COUNT
        check(metrics.size == expectedCount) { "Candidate raw-path matrix size changed." }
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
        metrics.groupBy { metric -> metric.descriptor.workloadIdentity() }.values.forEach { workloadMetrics ->
            val expectedState = workloadMetrics.first().outcome.state
            val expectedCorrectness = workloadMetrics.first().outcome.correctness
            val expectedResult = workloadMetrics.first().outcome.result
            workloadMetrics.forEach { metric ->
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

    private fun assertCrossFactorCorrectness(metrics: List<P2CandidateRawPathMeasurementMetric>) {
        val duplicateMetrics = metrics.filter { metric -> metric.descriptor.operation.isDuplicateChanged() }
        duplicateMetrics
            .groupBy { metric -> metric.descriptor.canvas to metric.descriptor.configuration }
            .values
            .forEach(::assertFactorGroup)
    }

    private fun assertFactorGroup(metrics: List<P2CandidateRawPathMeasurementMetric>) {
        check(metrics.size == P2CandidateRawPathMeasurementMatrix.duplicateFactors.size) {
            "Candidate raw-path factor group was incomplete."
        }
        val ordered = metrics.sortedBy { metric -> metric.descriptor.repeatFactor }
        val actualFactors = ordered.map { metric -> metric.descriptor.repeatFactor }
        check(actualFactors == P2CandidateRawPathMeasurementMatrix.duplicateFactors) {
            "Candidate raw-path factor group changed."
        }
        check(ordered.map { metric -> metric.outcome.correctness.rawInputDigest }.toSet().size == ordered.size) {
            "Candidate raw-path factor inputs were not distinct."
        }
        check(ordered.map { metric -> metric.outcome.withoutRawIdentity() }.toSet().size == 1) {
            "Candidate raw-path semantics or storage differed across factors."
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
            val rawPositions = descriptor.rawPositions()
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

internal fun P2CandidateRawPathOperationKind.isNoOp(): Boolean =
    this == P2CandidateRawPathOperationKind.ReferenceClearNoOp ||
        this == P2CandidateRawPathOperationKind.SameColorNoOp

private fun P2CandidateRawPathOperationKind.isDuplicateChanged(): Boolean =
    this == P2CandidateRawPathOperationKind.DuplicateChanged

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

private fun P2CandidateRawPathMeasurementDescriptor.rawPositions(): IntArray {
    val pixelCount = canvas.pixelCount.toInt()
    check(pathPositions == Math.multiplyExact(pixelCount, repeatFactor)) {
        "Candidate raw-path exact position count changed."
    }
    return IntArray(pathPositions) { index -> index / repeatFactor }
}

private fun P2CandidateRawPathMeasurementWork.inputOrder(): String =
    if (operation.isDuplicateChanged()) {
        repeatFactor.inputOrder()
    } else {
        "row_major"
    }

private fun Int.inputOrder(): String =
    when (this) {
        1 -> "row_major"
        2 -> "paired_row_major"
        4 -> "quadrupled_row_major"
        8 -> "octupled_row_major"
        else -> error("Unsupported candidate raw-path repeat factor: $this")
    }

private fun P2CandidateRawPathOperationKind.contentKind(): String =
    when (this) {
        P2CandidateRawPathOperationKind.DuplicateChanged -> "uniform_opaque_black_to_opaque_red"
        P2CandidateRawPathOperationKind.ReferenceClearChanged -> "uniform_opaque_red_to_reference_black"
        P2CandidateRawPathOperationKind.ReferenceClearNoOp -> "uniform_reference_black_to_same_black"
        P2CandidateRawPathOperationKind.SameColorNoOp -> "uniform_opaque_red_to_same_red"
    }

private fun P2CandidateRawPathOperationKind.expectedResultKind(): String = if (isNoOp()) "NoChanges" else "Rasterized"

private fun P2CandidateRawPathMeasurementDescriptor.workloadIdentity(): P2CandidateRawPathWorkloadIdentity =
    P2CandidateRawPathWorkloadIdentity(
        operation = operation,
        canvas = canvas,
        counts = listOf(pathPositions, uniquePathPositions, duplicatePathPositions, changeCount),
        contentKind = contentKind,
        inputOrder = protocol.inputOrder,
    )

private fun P2CandidateRawPathMeasurementOutcome.withoutRawIdentity(): P2CandidateRawPathMeasurementOutcome =
    copy(correctness = correctness.copy(rawInputDigest = ""))

private data class P2CandidateRawPathWorkloadIdentity(
    val operation: P2CandidateRawPathOperationKind,
    val canvas: P2CanvasShape,
    val counts: List<Int>,
    val contentKind: String,
    val inputOrder: String,
)

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
