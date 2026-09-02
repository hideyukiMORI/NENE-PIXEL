package io.github.hideyukimori.nenepixel.core.application.document.history

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.appliedSnapshot
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.patch
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.snapshot
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelChange
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatch
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchApplicationResult
import io.github.hideyukimori.nenepixel.core.pixelengine.StrokeRasterizationResult
import io.github.hideyukimori.nenepixel.core.pixelengine.rasterizeStroke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.fail
import java.security.MessageDigest

internal object P2CurrentRawAcceptanceMeasurement {
    fun measure(runner: P2HostMeasurementRunner): List<P2MeasurementMetric> {
        val groupVerifier = P2RawAcceptanceGroupVerifier()
        val metrics =
            P2CurrentRawAcceptanceMatrix.workloads.map { workload ->
                val fixture = fixture(workload)
                fixture.verifyUnchanged()
                groupVerifier.observe(fixture)
                val metric =
                    runner.measure(P2CurrentRawAcceptanceMatrix.descriptor(workload)) {
                        measuredOperation(fixture)
                    }
                fixture.verifyUnchanged()
                metric
            }
        groupVerifier.verifyComplete()
        P2CurrentRawAcceptanceMatrix.validateMetrics(metrics)
        return metrics
    }

    internal fun verifyFixedFixtureContract() {
        val groupVerifier = P2RawAcceptanceGroupVerifier()
        P2CurrentRawAcceptanceMatrix.workloads.forEach { workload ->
            val fixture = fixture(workload)
            fixture.verifyUnchanged()
            groupVerifier.observe(fixture)
        }
        groupVerifier.verifyComplete()
    }

    private fun measuredOperation(
        fixture: P2RawAcceptanceFixture,
    ): P2MeasuredOperation<StrokeRasterizationResult, P2RawAcceptanceResultEvidence> =
        P2MeasuredOperation(
            execute = {
                val stroke = Stroke.create(fixture.canvas, fixture.rawPath, red).requiredStroke()
                rasterizeStroke(fixture.source, stroke)
            },
            verify = { result -> verifyRasterized(result, fixture) },
            deterministicKey = { result -> result.evidence(fixture) },
        )

    private fun verifyRasterized(
        result: StrokeRasterizationResult,
        fixture: P2RawAcceptanceFixture,
    ) {
        val actualPatch = assertInstanceOf(StrokeRasterizationResult.Rasterized::class.java, result).patch
        assertEquals(fixture.expectedPatch, actualPatch)
        assertEquals(fixture.expectedInverse, actualPatch.inverse())
        assertEquals(fixture.expectedRegion, actualPatch.affectedRegion)
        assertEquals(fixture.workload.pixelCount, actualPatch.changeCount)
        val applied = appliedSnapshot(actualPatch.applyTo(fixture.source))
        assertEquals(fixture.expectedApplied, applied)
        assertEquals(fixture.source, actualPatch.inverse().applyTo(applied).requiredSnapshot())
        fixture.verifyUnchanged()
    }

    private fun fixture(workload: P2RawAcceptanceWorkload): P2RawAcceptanceFixture {
        val size = canvas(workload.shape.width, workload.shape.height)
        val source = snapshot(size)
        val uniquePath = rowMajorPath(size)
        val rawPath = List(workload.pathPositions) { index -> uniquePath[index / workload.factor] }
        val expectedPatch =
            patch(
                canvas = size,
                beforeRevision = source.revision,
                changes = uniquePath.map { point -> PixelChange.create(point, black, red) },
            )
        val expectedRegion = fullRegion(size)
        val expectedApplied = snapshot(size, revision(1L), List(workload.pixelCount) { red })
        assertEquals(expectedRegion, expectedPatch.affectedRegion)
        assertEquals(expectedApplied, appliedSnapshot(expectedPatch.applyTo(source)))
        val restored = appliedSnapshot(expectedPatch.inverse().applyTo(expectedApplied))
        assertEquals(source, restored)
        return P2RawAcceptanceFixture(
            workload = workload,
            canvas = size,
            rawPath = rawPath,
            rawDigest = rawPath.digest(size),
            source = source,
            sourceDigest = source.digest(),
            expectedPatch = expectedPatch,
            expectedInverse = expectedPatch.inverse(),
            expectedApplied = expectedApplied,
            expectedRegion = expectedRegion,
        )
    }

    private fun rowMajorPath(size: CanvasSize): List<PixelPosition> =
        List(size.pixelCount.toInt()) { index ->
            position(index % size.width.value, index / size.width.value)
        }

    private fun fullRegion(size: CanvasSize): PixelRegion =
        PixelRegion.create(size, position(0, 0), size).requiredValue()

    private fun StrokeRasterizationResult.evidence(fixture: P2RawAcceptanceFixture): P2RawAcceptanceResultEvidence {
        val actualPatch = assertInstanceOf(StrokeRasterizationResult.Rasterized::class.java, this).patch
        val applied = appliedSnapshot(actualPatch.applyTo(fixture.source))
        return P2RawAcceptanceResultEvidence(
            patch = actualPatch,
            inverse = actualPatch.inverse(),
            applied = applied,
            restored = appliedSnapshot(actualPatch.inverse().applyTo(applied)),
            rawDigest = fixture.rawDigest,
        )
    }

    private fun DomainValueResult<Stroke>.requiredStroke(): Stroke =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> fail("Raw-acceptance stroke was rejected: $rejection")
        }

    private fun <T> DomainValueResult<T>.requiredValue(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> fail("Raw-acceptance fixture value was rejected: $rejection")
        }

    private fun PixelPatchApplicationResult.requiredSnapshot(): PixelSnapshot =
        when (this) {
            is PixelPatchApplicationResult.Applied -> snapshot
            is PixelPatchApplicationResult.Rejected -> fail("Raw-acceptance patch application was rejected: $rejection")
        }
}

internal object P2CurrentRawAcceptanceMatrix {
    const val METRIC_NAME: String = "p2_current_raw_acceptance_duplicate_changed"
    const val METRIC_COUNT: Int = 28
    const val RAW_SAMPLE_COUNT: Int = 280
    const val CURRENT_METRIC_COUNT: Int = 49
    const val CURRENT_RAW_SAMPLE_COUNT: Int = 454

    private val shapes: List<P2CanvasShape> =
        listOf(
            P2CanvasShape(64, 64),
            P2CanvasShape(16, 256),
            P2CanvasShape(256, 16),
            P2CanvasShape(128, 128),
            P2CanvasShape(64, 256),
            P2CanvasShape(256, 64),
            P2CanvasShape(256, 256),
        )
    private val factors: List<Int> = listOf(1, 2, 4, 8)

    val workloads: List<P2RawAcceptanceWorkload> =
        shapes.flatMap { shape ->
            factors.map { factor -> P2RawAcceptanceWorkload(shape, factor, inputOrder(factor)) }
        }

    fun descriptor(workload: P2RawAcceptanceWorkload): P2MeasurementDescriptor =
        P2MeasurementDescriptor(
            name = METRIC_NAME,
            workload =
                P2WorkloadShape(
                    canvas = workload.shape,
                    pathPositions = workload.pathPositions,
                    changeCount = workload.pixelCount,
                    historyEntries = 0,
                ),
            sampling = SAMPLING,
            boundary = BOUNDARY,
        )

    fun validateMetrics(metrics: List<P2MeasurementMetric>) {
        validateDescriptors(metrics.map(P2MeasurementMetric::descriptor))
        metrics.forEach { metric ->
            require(metric.samples.latenciesNanos.size == SAMPLING.sampleCount) {
                "Raw-acceptance latency sample count does not match its fixed plan."
            }
            require(metric.samples.allocatedBytes.size == SAMPLING.sampleCount) {
                "Raw-acceptance allocation sample count does not match its fixed plan."
            }
        }
    }

    fun validateDescriptors(descriptors: List<P2MeasurementDescriptor>) {
        val expected = workloads.map(::descriptor)
        require(descriptors.size == METRIC_COUNT) {
            "Expected $METRIC_COUNT raw-acceptance metrics, found ${descriptors.size}."
        }
        require(descriptors.distinct().size == descriptors.size) {
            "Raw-acceptance matrix contains a duplicate shape/factor descriptor."
        }
        require(descriptors == expected) {
            "Raw-acceptance matrix is missing, reordered, or semantically different from the fixed protocol."
        }
    }

    private fun inputOrder(factor: Int): String =
        when (factor) {
            1 -> "row_major"
            2 -> "paired_row_major"
            4 -> "quadrupled_row_major"
            8 -> "octupled_row_major"
            else -> error("Unsupported raw-acceptance factor: $factor")
        }

    private val SAMPLING: P2SamplingPlan = P2SamplingPlan(warmupIterations = 5, sampleCount = 10)
    private const val BOUNDARY: String =
        "Stroke.create valid raw path through one rasterizeStroke call; " +
            "containment scan, defensive path copy, duplicate collapse, source filter, change collection, " +
            "and PixelPatch.create included"
}

internal data class P2RawAcceptanceWorkload(
    val shape: P2CanvasShape,
    val factor: Int,
    val inputOrder: String,
) {
    val pixelCount: Int = Math.multiplyExact(shape.width, shape.height)
    val pathPositions: Int = Math.multiplyExact(pixelCount, factor)
    val duplicatePositions: Int = pathPositions - pixelCount

    init {
        require(factor > 0)
        require(pathPositions > 0)
    }
}

private class P2RawAcceptanceGroupVerifier {
    private var currentShape: P2CanvasShape? = null
    private var baseline: P2RawAcceptanceSemanticEvidence? = null
    private val rawDigests: MutableSet<String> = mutableSetOf()
    private var factorCount: Int = 0

    fun observe(fixture: P2RawAcceptanceFixture) {
        if (currentShape != null && currentShape != fixture.workload.shape) verifyCurrentGroup()
        if (currentShape != fixture.workload.shape) reset(fixture.workload.shape)
        val evidence = fixture.semanticEvidence()
        baseline?.let { expected -> assertEquals(expected, evidence) } ?: run { baseline = evidence }
        require(rawDigests.add(fixture.rawDigest)) {
            "Raw-input digest is not distinct within shape ${fixture.workload.shape}."
        }
        factorCount += 1
    }

    fun verifyComplete() {
        verifyCurrentGroup()
    }

    private fun reset(shape: P2CanvasShape) {
        currentShape = shape
        baseline = null
        rawDigests.clear()
        factorCount = 0
    }

    private fun verifyCurrentGroup() {
        require(factorCount == FACTORS_PER_SHAPE) {
            "Shape $currentShape has $factorCount raw-acceptance factors instead of $FACTORS_PER_SHAPE."
        }
        require(rawDigests.size == FACTORS_PER_SHAPE) {
            "Shape $currentShape does not have one distinct raw digest per factor."
        }
    }

    private companion object {
        const val FACTORS_PER_SHAPE: Int = 4
    }
}

private data class P2RawAcceptanceFixture(
    val workload: P2RawAcceptanceWorkload,
    val canvas: CanvasSize,
    val rawPath: List<PixelPosition>,
    val rawDigest: String,
    val source: PixelSnapshot,
    val sourceDigest: P2RawAcceptanceSnapshotDigest,
    val expectedPatch: PixelPatch,
    val expectedInverse: PixelPatch,
    val expectedApplied: PixelSnapshot,
    val expectedRegion: PixelRegion,
) {
    fun verifyUnchanged() {
        assertEquals(workload.pathPositions, rawPath.size)
        assertEquals(rawDigest, rawPath.digest(canvas))
        assertEquals(sourceDigest, source.digest())
        assertEquals(workload.pixelCount, expectedPatch.changeCount)
        assertEquals(expectedPatch, expectedInverse.inverse())
        assertEquals(expectedRegion, expectedPatch.affectedRegion)
        assertEquals(expectedApplied, appliedSnapshot(expectedPatch.applyTo(source)))
        assertEquals(source, expectedInverse.applyTo(expectedApplied).requiredSnapshot())
    }

    fun semanticEvidence(): P2RawAcceptanceSemanticEvidence =
        P2RawAcceptanceSemanticEvidence(
            patch = expectedPatch,
            inverse = expectedInverse,
            region = expectedRegion,
            applied = expectedApplied,
            restored = expectedInverse.applyTo(expectedApplied).requiredSnapshot(),
        )
}

private data class P2RawAcceptanceSemanticEvidence(
    val patch: PixelPatch,
    val inverse: PixelPatch,
    val region: PixelRegion,
    val applied: PixelSnapshot,
    val restored: PixelSnapshot,
)

private data class P2RawAcceptanceResultEvidence(
    val patch: PixelPatch,
    val inverse: PixelPatch,
    val applied: PixelSnapshot,
    val restored: PixelSnapshot,
    val rawDigest: String,
)

private data class P2RawAcceptanceSnapshotDigest(
    val pixelCount: Long,
    val revision: Long,
    val contentHash: Int,
)

private fun PixelSnapshot.digest(): P2RawAcceptanceSnapshotDigest =
    P2RawAcceptanceSnapshotDigest(size.pixelCount, revision.value, hashCode())

private fun List<PixelPosition>.digest(canvas: CanvasSize): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.updateInt(canvas.width.value)
    digest.updateInt(canvas.height.value)
    digest.updateInt(size)
    forEach { point ->
        digest.updateInt(point.x.value)
        digest.updateInt(point.y.value)
    }
    return digest.digest().hex()
}

private fun MessageDigest.updateInt(value: Int) {
    update((value ushr 24).toByte())
    update((value ushr 16).toByte())
    update((value ushr 8).toByte())
    update(value.toByte())
}

private fun ByteArray.hex(): String {
    val bytes = this
    return buildString(size * 2) {
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0f])
        }
    }
}

private fun PixelPatchApplicationResult.requiredSnapshot(): PixelSnapshot =
    when (this) {
        is PixelPatchApplicationResult.Applied -> snapshot
        is PixelPatchApplicationResult.Rejected -> fail("Raw-acceptance fixture application was rejected: $rejection")
    }

private const val HEX_DIGITS: String = "0123456789abcdef"
