package io.github.hideyukimori.nenepixel.core.application.document.history

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import kotlin.math.ceil

internal enum class P2CandidateOperationKind(
    val csvName: String,
) {
    SnapshotBuild("snapshot_build"),
    ApplyOne("apply_one"),
    ApplyDense("apply_dense"),
}

internal enum class P2CandidateContentKind(
    val csvName: String,
) {
    HighEntropyRgba("deterministic_high_entropy_rgba"),
}

internal enum class P2CandidatePathKind(
    val csvName: String,
) {
    None("none"),
    OnePixel("one_pixel"),
    FullCanvasRowMajor("full_canvas_row_major"),
}

internal data class P2CandidateMeasurementDescriptor(
    val representation: P2CandidateRepresentation,
    val operation: P2CandidateOperationKind,
    val canvas: P2CanvasShape,
    val boundary: String,
) {
    val pathKind: P2CandidatePathKind
        get() =
            when (operation) {
                P2CandidateOperationKind.SnapshotBuild -> P2CandidatePathKind.None
                P2CandidateOperationKind.ApplyOne -> P2CandidatePathKind.OnePixel
                P2CandidateOperationKind.ApplyDense -> P2CandidatePathKind.FullCanvasRowMajor
            }

    val changeCount: Int
        get() =
            when (operation) {
                P2CandidateOperationKind.SnapshotBuild -> 0
                P2CandidateOperationKind.ApplyOne -> 1
                P2CandidateOperationKind.ApplyDense -> canvas.pixelCount.toInt()
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
    val patchPayloadBytes: Long,
    val units: P2CandidateUnitCounts,
    val correctness: P2CandidateCorrectness,
)

internal data class P2CandidateMeasurementMetric(
    val descriptor: P2CandidateMeasurementDescriptor,
    val samples: P2RawSamples,
    val percentiles: P2CandidatePercentiles,
    val outcome: P2CandidateMeasurementOutcome,
)

internal data class P2CandidatePercentiles(
    val latency: P2Percentiles,
    val allocation: P2Percentiles,
)

internal object P2CandidateMeasurement {
    fun measure(allocationCounter: P2ThreadAllocationCounter): List<P2CandidateMeasurementMetric> =
        CANDIDATE_CANVASES.flatMap { canvas ->
            P2CandidateRepresentation.entries
                .filterNot { representation -> representation == P2CandidateRepresentation.PaletteValueU8 }
                .flatMap { representation ->
                    P2CandidateOperationKind.entries.map { operation ->
                        measureCandidate(allocationCounter, descriptor(representation, operation, canvas))
                    }
                }
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
        representation: P2CandidateRepresentation,
        operation: P2CandidateOperationKind,
        canvas: P2CanvasShape,
    ): P2CandidateMeasurementDescriptor =
        P2CandidateMeasurementDescriptor(
            representation = representation,
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

    private const val WARMUP_ITERATIONS: Int = 5
    private const val SAMPLE_COUNT: Int = 10
    private const val MEDIAN_PERCENTILE: Double = 0.50
    private const val P95_PERCENTILE: Double = 0.95
    private const val P99_PERCENTILE: Double = 0.99
    private val CANDIDATE_CANVASES: List<P2CanvasShape> =
        listOf(
            P2CanvasShape(64, 64),
            P2CanvasShape(128, 128),
            P2CanvasShape(256, 256),
        )
}

private class P2CandidateMeasurementFixture private constructor(
    private val descriptor: P2CandidateMeasurementDescriptor,
    private val input: P2CandidateSemanticInput,
    private val initial: P2CandidateSnapshot?,
    private val changes: P2CandidateChanges?,
) {
    fun execute(): P2CandidateExecution =
        when (descriptor.operation) {
            P2CandidateOperationKind.SnapshotBuild -> {
                P2CandidateExecution.Built(buildSnapshot())
            }

            P2CandidateOperationKind.ApplyOne,
            P2CandidateOperationKind.ApplyDense,
            -> {
                P2CandidateExecution.Applied(
                    requireNotNull(initial).apply(requireNotNull(changes)),
                )
            }
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
            patchPayloadBytes = 0L,
            units = P2CandidateUnitCounts(0, 0, 0, descriptor.representation.tileEdge()),
            correctness = P2CandidateCorrectness(snapshot.semanticDigest(), snapshot.semanticDigest(), "pass"),
        )
    }

    private fun verifyApplication(application: P2CandidateApplication): P2CandidateMeasurementOutcome {
        val before = requireNotNull(initial)
        val patch = requireNotNull(changes)
        val expected = input.packed.copyOf()
        repeat(patch.changeCount) { index -> expected[patch.positionAt(index)] = patch.afterAt(index) }
        assertSemanticPixels(application.snapshot, expected)
        val restored = application.snapshot.apply(patch.inverse()).snapshot
        check(before == restored) { "Candidate inverse round trip failed." }
        return P2CandidateMeasurementOutcome(
            storage = copiedStorage(application),
            patchPayloadBytes = patch.primitivePayloadBytes,
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
        )
    }

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
        descriptor.representation.createSnapshot(descriptor.canvas, input.colors)

    companion object {
        fun create(descriptor: P2CandidateMeasurementDescriptor): P2CandidateMeasurementFixture {
            val packed = highEntropyPixels(descriptor.canvas)
            val input = P2CandidateSemanticInput(packed.map(P2PackedRgba8888::unpack), packed)
            if (descriptor.operation == P2CandidateOperationKind.SnapshotBuild) {
                return P2CandidateMeasurementFixture(descriptor, input, null, null)
            }
            val initial = descriptor.representation.createSnapshot(descriptor.canvas, input.colors)
            val positions = descriptor.changePositions()
            val after = positions.map { position -> packed[position] xor ALPHA_XOR_MASK }.toIntArray()
            val changes = P2CandidateChanges.create(initial, positions, after)
            return P2CandidateMeasurementFixture(descriptor, input, initial, changes)
        }

        private fun highEntropyPixels(canvas: P2CanvasShape): IntArray =
            IntArray(canvas.pixelCount.toInt(), ::highEntropyPacked)

        private fun highEntropyPacked(index: Int): Int =
            ((index and CHANNEL_MASK) shl RED_SHIFT) or
                ((index ushr BYTE_BITS and CHANNEL_MASK) shl GREEN_SHIFT) or
                ((index * BLUE_MULTIPLIER and CHANNEL_MASK) shl BLUE_SHIFT) or
                (index * ALPHA_MULTIPLIER and CHANNEL_MASK)

        private const val RED_SHIFT: Int = 24
        private const val GREEN_SHIFT: Int = 16
        private const val BLUE_SHIFT: Int = 8
        private const val BYTE_BITS: Int = 8
        private const val CHANNEL_MASK: Int = 0xff
        private const val BLUE_MULTIPLIER: Int = 29
        private const val ALPHA_MULTIPLIER: Int = 43
        private const val ALPHA_XOR_MASK: Int = 0x000000ff
    }
}

private sealed interface P2CandidateExecution {
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

private fun P2CandidateRepresentation.createSnapshot(
    canvas: P2CanvasShape,
    colors: List<PixelColor>,
): P2CandidateSnapshot =
    when (this) {
        P2CandidateRepresentation.CurrentObjectList -> {
            P2CurrentObjectCandidateSnapshot.create(canvas, 0L, colors)
        }

        P2CandidateRepresentation.FlatPackedRgba8888 -> {
            P2FlatPackedCandidateSnapshot.create(canvas, 0L, colors.packed())
        }

        P2CandidateRepresentation.TiledCowRgba8888T16 -> {
            P2TiledCowCandidateSnapshot.create(canvas, 0L, colors.packed(), tileEdge = 16)
        }

        P2CandidateRepresentation.TiledCowRgba8888T32 -> {
            P2TiledCowCandidateSnapshot.create(canvas, 0L, colors.packed(), tileEdge = 32)
        }

        P2CandidateRepresentation.TiledCowRgba8888T64 -> {
            P2TiledCowCandidateSnapshot.create(canvas, 0L, colors.packed(), tileEdge = 64)
        }

        P2CandidateRepresentation.PaletteValueU8 -> {
            error("Palette performance remains semantically pending.")
        }
    }

private fun List<PixelColor>.packed(): IntArray = map(P2PackedRgba8888::pack).toIntArray()

private fun P2CandidateRepresentation.tileEdge(): Int =
    when (this) {
        P2CandidateRepresentation.TiledCowRgba8888T16 -> 16
        P2CandidateRepresentation.TiledCowRgba8888T32 -> 32
        P2CandidateRepresentation.TiledCowRgba8888T64 -> 64
        else -> 0
    }

private fun P2CandidateMeasurementDescriptor.changePositions(): IntArray =
    when (operation) {
        P2CandidateOperationKind.SnapshotBuild -> error("Snapshot build has no change positions.")
        P2CandidateOperationKind.ApplyOne -> intArrayOf(canvas.pixelCount.toInt() / 2)
        P2CandidateOperationKind.ApplyDense -> IntArray(canvas.pixelCount.toInt()) { index -> index }
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
