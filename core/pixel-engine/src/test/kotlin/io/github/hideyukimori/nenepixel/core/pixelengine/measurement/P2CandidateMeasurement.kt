package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

import com.sun.management.ThreadMXBean
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import java.lang.management.ManagementFactory
import kotlin.math.ceil

internal enum class P2CandidateOperationKind(
    val csvName: String,
    val pathKind: P2CandidatePathKind,
    val contentKind: P2CandidateContentKind,
) {
    SnapshotBuildOneColor("snapshot_build", P2CandidatePathKind.None, P2CandidateContentKind.OneColor),
    SnapshotBuild256Colors("snapshot_build", P2CandidatePathKind.None, P2CandidateContentKind.Colors256),
    SnapshotBuildHighEntropy("snapshot_build", P2CandidatePathKind.None, P2CandidateContentKind.HighEntropyRgba),
    ApplyOne("apply_one", P2CandidatePathKind.OnePixel, P2CandidateContentKind.HighEntropyRgba),
    ApplyDiagonal("apply_diagonal", P2CandidatePathKind.Diagonal, P2CandidateContentKind.HighEntropyRgba),
    ApplyRow("apply_row", P2CandidatePathKind.FullRow, P2CandidateContentKind.HighEntropyRgba),
    ApplyColumn("apply_column", P2CandidatePathKind.FullColumn, P2CandidateContentKind.HighEntropyRgba),
    ApplyQuarter("apply_25_percent", P2CandidatePathKind.QuarterSerpentine, P2CandidateContentKind.HighEntropyRgba),
    ApplyHalf("apply_50_percent", P2CandidatePathKind.HalfSerpentine, P2CandidateContentKind.HighEntropyRgba),
    ApplyDense("apply_dense", P2CandidatePathKind.FullCanvasSerpentine, P2CandidateContentKind.HighEntropyRgba),
    ;

    val isSnapshotBuild: Boolean
        get() = pathKind == P2CandidatePathKind.None
}

internal enum class P2CandidateContentKind(
    val csvName: String,
) {
    OneColor("one_semantic_color"),
    Colors256("exactly_256_semantic_colors"),
    HighEntropyRgba("deterministic_high_entropy_rgba"),
}

internal enum class P2CandidatePathKind(
    val csvName: String,
) {
    None("none"),
    OnePixel("one_pixel"),
    Diagonal("diagonal"),
    FullRow("full_row"),
    FullColumn("full_column"),
    QuarterSerpentine("quarter_serpentine"),
    HalfSerpentine("half_serpentine"),
    FullCanvasSerpentine("full_canvas_serpentine"),
}

internal data class P2CandidateMeasurementDescriptor(
    val configuration: P2CandidateConfiguration,
    val operation: P2CandidateOperationKind,
    val canvas: P2CanvasShape,
    val boundary: String,
) {
    val representation: P2CandidateRepresentation
        get() = configuration.snapshotRepresentation

    val pathKind: P2CandidatePathKind
        get() = operation.pathKind

    val changeCount: Int
        get() = pathKind.changeCount(canvas)

    val colorCardinality: Long
        get() =
            when (operation.contentKind) {
                P2CandidateContentKind.OneColor -> 1L
                P2CandidateContentKind.Colors256 -> minOf(COLOR_SET_SIZE, canvas.pixelCount)
                P2CandidateContentKind.HighEntropyRgba -> canvas.pixelCount
            }

    private companion object {
        const val COLOR_SET_SIZE: Long = 256L
    }
}

internal data class P2CandidateUnitCounts(
    val touched: Int,
    val copied: Int,
    val shared: Int,
    val tileEdge: Int,
)

internal data class P2CandidateCorrectness(
    val semanticDigest: Int,
    val inverseDigest: Int,
    val status: String,
)

internal data class P2CandidateMeasurementOutcome(
    val storage: P2CandidateStorageCounts,
    val patchStorage: P2CandidatePatchPairStorage,
    val units: P2CandidateUnitCounts,
    val correctness: P2CandidateCorrectness,
    val nativePatchAudit: P2CandidatePatchSharedAudit?,
)

internal data class P2CandidateMeasurementMetric(
    val descriptor: P2CandidateMeasurementDescriptor,
    val samples: P2RawSamples,
    val percentiles: P2CandidatePercentiles,
    val outcome: P2CandidateMeasurementOutcome,
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

internal data class P2CandidatePercentiles(
    val latency: P2Percentiles,
    val allocation: P2Percentiles,
)

internal object P2CandidateMeasurement {
    fun measure(allocationCounter: P2ThreadAllocationCounter): List<P2CandidateMeasurementMetric> {
        val metrics =
            CANDIDATE_CANVASES.flatMap { canvas ->
                P2CandidateConfiguration.entries.flatMap { configuration ->
                    P2CandidateOperationKind.entries.map { operation ->
                        measureCandidate(allocationCounter, descriptor(configuration, operation, canvas))
                    }
                }
            }
        assertSparseForwardAudits(metrics)
        return metrics
    }

    private fun measureCandidate(
        allocationCounter: P2ThreadAllocationCounter,
        descriptor: P2CandidateMeasurementDescriptor,
    ): P2CandidateMeasurementMetric {
        val fixture = P2CandidateMeasurementFixture.create(descriptor)
        var deterministicOutcome: P2CandidateMeasurementOutcome? = null
        repeat(WARMUP_ITERATIONS) {
            deterministicOutcome = deterministicOutcome.assertDeterministic(fixture.executeAndVerify())
        }
        val latencies = LongArray(SAMPLE_COUNT)
        val allocations = LongArray(SAMPLE_COUNT)
        repeat(SAMPLE_COUNT) { index ->
            val allocationBefore = allocationCounter.currentThreadBytes()
            val startedAtNanos = System.nanoTime()
            val execution = fixture.execute()
            latencies[index] = System.nanoTime() - startedAtNanos
            allocations[index] = allocationCounter.currentThreadBytes() - allocationBefore
            deterministicOutcome = deterministicOutcome.assertDeterministic(fixture.verify(execution))
        }
        return P2CandidateMeasurementMetric(
            descriptor = descriptor,
            samples = P2RawSamples(latencies, allocations),
            percentiles = P2CandidatePercentiles(latencies.percentiles(), allocations.percentiles()),
            outcome = requireNotNull(deterministicOutcome),
        )
    }

    private fun descriptor(
        configuration: P2CandidateConfiguration,
        operation: P2CandidateOperationKind,
        canvas: P2CanvasShape,
    ): P2CandidateMeasurementDescriptor =
        P2CandidateMeasurementDescriptor(
            configuration = configuration,
            operation = operation,
            canvas = canvas,
            boundary =
                "test-only candidate ${operation.csvName}; input generation and full semantic/inverse " +
                    "verification excluded",
        )

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

    private fun P2CandidateMeasurementOutcome?.assertDeterministic(
        actual: P2CandidateMeasurementOutcome,
    ): P2CandidateMeasurementOutcome {
        if (this != null) check(this == actual) { "Candidate measurement outcome was not deterministic." }
        return actual
    }

    private fun assertSparseForwardAudits(metrics: List<P2CandidateMeasurementMetric>) {
        val reused =
            metrics.filter { metric ->
                metric.descriptor.canvas in P2CandidatePatchMeasurementMatrix.sparseCanvases &&
                    !metric.descriptor.operation.isSnapshotBuild
            }
        check(reused.size == REUSED_FORWARD_METRIC_COUNT) { "Candidate reused forward matrix size changed." }
        check(reused.sumOf { metric -> metric.samples.latenciesNanos.size } == REUSED_FORWARD_SAMPLE_COUNT) {
            "Candidate reused forward sample count changed."
        }
        val grouped = reused.groupBy { metric -> metric.descriptor.canvas to metric.descriptor.pathKind }
        grouped.values.forEach { group ->
            check(group.size == P2CandidateConfiguration.entries.size) {
                "Candidate reused forward pair was incomplete."
            }
            val audits = group.map { metric -> requireNotNull(metric.outcome.nativePatchAudit) }.toSet()
            check(audits.size == 1) { "Candidate reused forward correctness differed across configurations." }
        }
    }

    private const val WARMUP_ITERATIONS: Int = 5
    private const val SAMPLE_COUNT: Int = 10
    private const val MEDIAN_PERCENTILE: Double = 0.50
    private const val P95_PERCENTILE: Double = 0.95
    private const val P99_PERCENTILE: Double = 0.99
    private const val REUSED_FORWARD_METRIC_COUNT: Int = 210
    private const val REUSED_FORWARD_SAMPLE_COUNT: Int = 2_100
    private val CANDIDATE_CANVASES: List<P2CanvasShape> =
        listOf(
            P2CanvasShape(64, 64),
            P2CanvasShape(16, 256),
            P2CanvasShape(256, 16),
            P2CanvasShape(128, 128),
            P2CanvasShape(64, 256),
            P2CanvasShape(256, 64),
            P2CanvasShape(256, 256),
        )
}

internal class P2CandidateMeasurementFixture private constructor(
    private val descriptor: P2CandidateMeasurementDescriptor,
    private val input: P2CandidateSemanticInput,
    private val initial: P2CandidateSnapshot?,
    private val patch: P2CandidatePatch?,
    private val workload: P2CandidateWorkloadFixture?,
) {
    fun execute(): P2CandidateExecution =
        if (descriptor.operation.isSnapshotBuild) {
            P2CandidateExecution.Built(buildSnapshot())
        } else {
            P2CandidateExecution.Applied(
                requireNotNull(initial).apply(requireNotNull(patch)).requiredApplication(),
            )
        }

    fun verify(execution: P2CandidateExecution): P2CandidateMeasurementOutcome =
        when (execution) {
            is P2CandidateExecution.Built -> verifyBuild(execution.snapshot)
            is P2CandidateExecution.Applied -> verifyApplication(execution.application)
        }

    fun executeAndVerify(): P2CandidateMeasurementOutcome = verify(execute())

    private fun verifyBuild(snapshot: P2CandidateSnapshot): P2CandidateMeasurementOutcome {
        assertSemanticPixels(snapshot, input.packed)
        return P2CandidateMeasurementOutcome(
            storage = snapshot.storage,
            patchStorage = emptyPatchStorage(),
            units = P2CandidateUnitCounts(0, 0, 0, descriptor.representation.tileEdge()),
            correctness = P2CandidateCorrectness(snapshot.semanticDigest(), snapshot.semanticDigest(), "pass"),
            nativePatchAudit = null,
        )
    }

    private fun verifyApplication(application: P2CandidateApplication): P2CandidateMeasurementOutcome {
        val before = requireNotNull(initial)
        val changes = requireNotNull(patch)
        val expected = input.packed.copyOf()
        repeat(changes.changeCount) { index -> expected[changes.positionAt(index)] = changes.afterAt(index) }
        assertSemanticPixels(application.snapshot, expected)
        val inverse = changes.inverse()
        val restored =
            application.snapshot
                .apply(inverse)
                .requiredApplication()
                .snapshot
        assertSemanticPixels(restored, input.packed)
        check(before == restored) { "Candidate inverse round trip failed." }
        val audit = nativePatchAudit(before, changes, inverse, application.snapshot)
        return P2CandidateMeasurementOutcome(
            storage = copiedStorage(application),
            patchStorage = changes.pairStorage(inverse),
            units =
                P2CandidateUnitCounts(
                    application.touchedUnits,
                    application.copiedUnits,
                    application.sharedUnits,
                    descriptor.representation.tileEdge(),
                ),
            correctness =
                P2CandidateCorrectness(
                    application.snapshot.semanticDigest(),
                    restored.semanticDigest(),
                    "pass",
                ),
            nativePatchAudit = audit,
        )
    }

    private fun nativePatchAudit(
        before: P2CandidateSnapshot,
        forward: P2CandidatePatch,
        inverse: P2CandidatePatch,
        applied: P2CandidateSnapshot,
    ): P2CandidatePatchSharedAudit =
        P2CandidatePatchVerification.audit(
            descriptor.configuration,
            P2CandidatePatchLifecycleFixture(before, forward, inverse, applied),
            requireNotNull(workload),
            P2CandidatePatchOperationSnapshots(before, applied),
        )

    private fun copiedStorage(application: P2CandidateApplication): P2CandidateStorageCounts {
        val retained = application.snapshot.storage
        val tileEdge = descriptor.representation.tileEdge()
        return if (retained.primitivePayloadBytes == 0L) {
            retained.copy(copiedReferenceSlots = application.copiedUnits.toLong())
        } else {
            val copiedValues =
                if (tileEdge == 0) {
                    application.copiedUnits.toLong()
                } else {
                    application.copiedUnits.toLong() * tileEdge * tileEdge
                }
            retained.copy(copiedPrimitiveBytes = copiedValues * Int.SIZE_BYTES)
        }
    }

    private fun buildSnapshot(): P2CandidateSnapshot =
        descriptor.configuration.createSnapshot(descriptor.canvas, input.colors)

    companion object {
        fun create(descriptor: P2CandidateMeasurementDescriptor): P2CandidateMeasurementFixture {
            val packed = semanticPixels(descriptor.canvas, descriptor.operation.contentKind)
            val input = P2CandidateSemanticInput(packed.map(P2PackedRgba8888::unpack), packed)
            if (descriptor.operation.isSnapshotBuild) {
                return P2CandidateMeasurementFixture(descriptor, input, null, null, null)
            }
            val workload = P2CandidateWorkloadFixture.create(descriptor.canvas, descriptor.pathKind)
            check(workload.initialPixels.contentEquals(packed)) { "Candidate workload semantic input changed." }
            val initial = descriptor.configuration.createSnapshot(descriptor.canvas, input.colors)
            val positions = workload.pathPositions
            val patch =
                P2CandidatePatchFactory
                    .create(
                        descriptor.configuration,
                        initial,
                        positions,
                        workload.afterColors(positions),
                    ).requiredPatch()
            val reversePatch =
                P2CandidatePatchFactory
                    .create(
                        descriptor.configuration,
                        initial,
                        workload.reverseCanonicalPositions,
                        workload.afterColors(workload.reverseCanonicalPositions),
                    ).requiredPatch()
            P2CandidatePatchVerification.verifyEquivalent(patch, reversePatch)
            return P2CandidateMeasurementFixture(descriptor, input, initial, patch, workload)
        }

        private fun semanticPixels(
            canvas: P2CanvasShape,
            content: P2CandidateContentKind,
        ): IntArray =
            when (content) {
                P2CandidateContentKind.OneColor -> {
                    IntArray(canvas.pixelCount.toInt()) { ONE_COLOR_PACKED }
                }

                P2CandidateContentKind.Colors256 -> {
                    IntArray(canvas.pixelCount.toInt()) { index ->
                        P2CandidateWorkloadFixture.highEntropyPacked(index % COLOR_SET_SIZE)
                    }
                }

                P2CandidateContentKind.HighEntropyRgba -> {
                    P2CandidateWorkloadFixture.highEntropyPixels(canvas)
                }
            }

        private const val COLOR_SET_SIZE: Int = 256
        private const val ONE_COLOR_PACKED: Int = 0x336699cc
    }
}

internal sealed interface P2CandidateExecution {
    data class Built(
        val snapshot: P2CandidateSnapshot,
    ) : P2CandidateExecution

    data class Applied(
        val application: P2CandidateApplication,
    ) : P2CandidateExecution
}

private data class P2CandidateSemanticInput(
    val colors: List<PixelColor>,
    val packed: IntArray,
)

internal fun P2CandidateConfiguration.createSnapshot(
    canvas: P2CanvasShape,
    colors: List<PixelColor>,
): P2CandidateSnapshot =
    if (snapshotRepresentation == P2CandidateRepresentation.CurrentObjectList) {
        P2CurrentObjectCandidateSnapshot.create(canvas, 0L, colors)
    } else {
        createSnapshot(canvas, 0L, colors.packed())
    }

internal fun P2CandidateConfiguration.createSnapshot(
    canvas: P2CanvasShape,
    revision: Long,
    packed: IntArray,
): P2CandidateSnapshot =
    when (snapshotRepresentation) {
        P2CandidateRepresentation.CurrentObjectList -> {
            P2CurrentObjectCandidateSnapshot.create(canvas, revision, packed.map(P2PackedRgba8888::unpack))
        }

        P2CandidateRepresentation.FlatPackedRgba8888 -> {
            P2FlatPackedCandidateSnapshot.create(canvas, revision, packed)
        }

        P2CandidateRepresentation.TiledCowRgba8888T16 -> {
            P2TiledCowCandidateSnapshot.create(canvas, revision, packed, tileEdge = 16)
        }

        P2CandidateRepresentation.TiledCowRgba8888T32 -> {
            P2TiledCowCandidateSnapshot.create(canvas, revision, packed, tileEdge = 32)
        }

        P2CandidateRepresentation.TiledCowRgba8888T64 -> {
            P2TiledCowCandidateSnapshot.create(canvas, revision, packed, tileEdge = 64)
        }

        P2CandidateRepresentation.PaletteValueU8 -> {
            error("Palette performance remains semantically pending.")
        }
    }

private fun emptyPatchStorage(): P2CandidatePatchPairStorage =
    P2CandidatePatchPairStorage(
        forward = P2CandidatePatchStorageCounts.Empty,
        inverseAdditional = P2CandidatePatchStorageCounts.Empty,
        shared = P2CandidatePatchStorageCounts.Empty,
        retainedUnion = P2CandidatePatchStorageCounts.Empty,
    )

private fun List<PixelColor>.packed(): IntArray = map(P2PackedRgba8888::pack).toIntArray()

private fun P2CandidateRepresentation.tileEdge(): Int =
    when (this) {
        P2CandidateRepresentation.TiledCowRgba8888T16 -> 16
        P2CandidateRepresentation.TiledCowRgba8888T32 -> 32
        P2CandidateRepresentation.TiledCowRgba8888T64 -> 64
        else -> 0
    }

private fun assertSemanticPixels(
    snapshot: P2CandidateSnapshot,
    expected: IntArray,
) {
    check(snapshot.shape.pixelCount == expected.size.toLong()) { "Candidate semantic size mismatch." }
    expected.indices.forEach { index ->
        check(snapshot.packedAt(index) == expected[index]) { "Candidate semantic mismatch at index $index." }
    }
}

internal class P2ThreadAllocationCounter private constructor(
    private val bean: ThreadMXBean,
) {
    fun currentThreadBytes(): Long = bean.getThreadAllocatedBytes(Thread.currentThread().threadId())

    companion object {
        fun create(): P2ThreadAllocationCounter {
            val bean = ManagementFactory.getThreadMXBean()
            require(bean is ThreadMXBean && bean.isThreadAllocatedMemorySupported) {
                "The named P2 representation profile requires HotSpot thread-allocation measurement support."
            }
            if (!bean.isThreadAllocatedMemoryEnabled) bean.isThreadAllocatedMemoryEnabled = true
            return P2ThreadAllocationCounter(bean)
        }
    }
}
