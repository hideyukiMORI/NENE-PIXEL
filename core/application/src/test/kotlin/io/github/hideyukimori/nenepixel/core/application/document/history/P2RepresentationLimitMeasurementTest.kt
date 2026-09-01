package io.github.hideyukimori.nenepixel.core.application.document.history

import com.sun.management.ThreadMXBean
import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.appliedSnapshot
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.colorAt
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.snapshot
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.application.document.transition.ChangeSet
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatch
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchApplicationResult
import io.github.hideyukimori.nenepixel.core.pixelengine.StrokeRasterizationResult
import io.github.hideyukimori.nenepixel.core.pixelengine.rasterizeStroke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.lang.management.ManagementFactory

internal class P2RepresentationLimitMeasurementTest {
    private val allocationCounter: P2ThreadAllocationCounter = P2ThreadAllocationCounter.create()
    private val measurementRunner: P2HostMeasurementRunner = P2HostMeasurementRunner(allocationCounter)

    @Test
    fun `measure current representation workloads and emit logical limit analysis`() {
        val metrics =
            snapshotMetrics() +
                commandMetrics() +
                patchMetrics() +
                historyMetrics() +
                P2CanonicalGapMeasurement.measure(measurementRunner)

        P2RepresentationMeasurementReport.write(
            metrics = metrics,
            analyses = analysisRows(),
        )
    }

    private fun snapshotMetrics(): List<P2MeasurementMetric> =
        SNAPSHOT_EDGES.map(::measureLowCardinalitySnapshot) + listOf(measureHighCardinalitySnapshot(DENSE_EDGE))

    private fun commandMetrics(): List<P2MeasurementMetric> =
        SPARSE_EDGES.map(::measureSparseCommand) +
            DENSE_EDGES.map(::measureDenseCommand) +
            listOf(
                measureDenseEraserEquivalent(DENSE_EDGE),
                measureDenseSameColorNoOp(DENSE_EDGE),
            )

    private fun patchMetrics(): List<P2MeasurementMetric> =
        listOf(
            measureDensePatchInverse(DENSE_EDGE),
            measureDensePatchApply(DENSE_EDGE, inverse = false),
            measureDensePatchApply(DENSE_EDGE, inverse = true),
        )

    private fun historyMetrics(): List<P2MeasurementMetric> =
        listOf(
            measureHistoryEntryRetention(edge = DENSE_EDGE, dense = false, historyEntries = 16),
            measureHistoryEntryRetention(edge = 64, dense = true, historyEntries = 8),
        )

    private fun measureLowCardinalitySnapshot(edge: Int): P2MeasurementMetric {
        val size = canvas(edge, edge)
        val pixels = List(size.pixelCount.toInt()) { black }
        val expected = snapshot(size)
        return measure(snapshotDescriptor("p2_snapshot_create_low_cardinality", edge)) {
            P2MeasuredOperation(
                execute = { PixelSnapshot.create(size, revision(0L), pixels) },
                verify = { result -> assertEquals(expected, result.requiredValue()) },
                deterministicKey = { result -> result.requiredValue().digest() },
            )
        }
    }

    private fun measureHighCardinalitySnapshot(edge: Int): P2MeasurementMetric {
        val size = canvas(edge, edge)
        val pixels = highCardinalityPixels(size)
        return measure(
            snapshotDescriptor("p2_snapshot_create_high_cardinality", edge),
        ) {
            P2MeasuredOperation(
                execute = { PixelSnapshot.create(size, revision(0L), pixels) },
                verify = { result ->
                    val created = result.requiredValue()
                    assertEquals(pixels.first(), colorAt(created, position(0, 0)))
                    assertEquals(pixels.last(), colorAt(created, position(edge - 1, edge - 1)))
                },
                deterministicKey = { result -> result.requiredValue().digest() },
            )
        }
    }

    private fun measureSparseCommand(edge: Int): P2MeasurementMetric {
        val descriptor =
            commandDescriptor(
                name = "p2_command_sparse_pencil_equivalent",
                edge = edge,
                pathPositions = edge,
                boundary = "CommandGateway.execute diagonal ApplyStrokeCommand; C=edge",
            )
        return measureCommand(
            descriptor = descriptor,
            path = { size -> diagonalPath(size, edge) },
        )
    }

    private fun measureDenseCommand(edge: Int): P2MeasurementMetric {
        val descriptor =
            commandDescriptor(
                name = "p2_command_dense_pencil_equivalent",
                edge = edge,
                pathPositions = edge * edge,
                boundary = "CommandGateway.execute row-major full-canvas ApplyStrokeCommand; C=N",
            )
        return measureCommand(descriptor, ::densePath)
    }

    private fun measureDenseEraserEquivalent(edge: Int): P2MeasurementMetric {
        val descriptor =
            commandDescriptor(
                name = "p2_command_dense_eraser_equivalent",
                edge = edge,
                pathPositions = edge * edge,
                boundary =
                    "full-canvas opaque red to the current black reference-clear fixture; " +
                        "not semantic blank or eraser selection",
            )
        return measureCommand(descriptor, ::densePath, initialColor = red, resultColor = black)
    }

    private fun measureDenseSameColorNoOp(edge: Int): P2MeasurementMetric {
        val size = canvas(edge, edge)
        val path = densePath(size)
        val initial = state(size, pixels = List(size.pixelCount.toInt()) { red })
        val measuredStroke = stroke(size, path, red)
        val command = ApplyStrokeCommand.create(initial.id, initial.revision, measuredStroke)
        val descriptor =
            P2MeasurementDescriptor(
                name = "p2_command_dense_same_color_noop",
                workload =
                    P2WorkloadShape(
                        canvas = P2CanvasShape(edge, edge),
                        pathPositions = path.size,
                        changeCount = 0,
                        historyEntries = 0,
                    ),
                sampling = P2SamplingPlan(DENSE_WARMUPS, DENSE_SAMPLES),
                boundary =
                    "CommandGateway.execute full-canvas opaque-red same-color stroke; " +
                        "typed no-effective-change",
            )
        return measure(descriptor) {
            val gateway = CommandGateway.create(initial)
            P2MeasuredOperation(
                execute = { gateway.execute(command) },
                verify = { result ->
                    val rejected = assertInstanceOf(CommandResult.Rejected::class.java, result)
                    assertEquals(RejectionReason.NoEffectiveChange, rejected.reason)
                    assertEquals(initial, gateway.runtimeState.documentState)
                    assertEquals(HistoryAvailability.None, gateway.runtimeState.historyAvailability)
                },
                deterministicKey = { result -> result },
            )
        }
    }

    private fun measureCommand(
        descriptor: P2MeasurementDescriptor,
        path: (CanvasSize) -> List<PixelPosition>,
        initialColor: PixelColor = black,
        resultColor: PixelColor = red,
    ): P2MeasurementMetric {
        val size = canvas(descriptor.workload.canvas.width, descriptor.workload.canvas.height)
        val positions = path(size)
        val initialPixels = List(descriptor.pixelCount.toInt()) { initialColor }
        val expectedPixels = initialPixels.toMutableList()
        positions.forEach { point -> expectedPixels[point.rowMajorIndex(size)] = resultColor }
        val initial = state(size, pixels = initialPixels)
        val expected = state(size, revision(1L), pixels = expectedPixels)
        val measuredStroke = stroke(size, positions, resultColor)
        val command = ApplyStrokeCommand.create(initial.id, initial.revision, measuredStroke)
        return measure(descriptor) {
            val gateway = CommandGateway.create(initial)
            P2MeasuredOperation(
                execute = { gateway.execute(command) },
                verify = { result ->
                    verifyAppliedCommand(
                        result,
                        gateway,
                        P2CommandVerification(
                            initial = initial,
                            expected = expected,
                            expectedChanges = positions.size,
                            expectedRegion = fullCanvasRegion(size),
                        ),
                    )
                },
                deterministicKey = { result -> result.requiredApplied().changeSet.digest() },
            )
        }
    }

    private fun verifyAppliedCommand(
        result: CommandResult,
        gateway: CommandGateway,
        verification: P2CommandVerification,
    ) {
        val applied = result.requiredApplied()
        val patch = applied.changeSet.patch
        assertEquals(verification.expectedChanges, patch.changeCount)
        assertEquals(verification.expectedRegion, patch.affectedRegion)
        assertEquals(patch.inverse(), applied.changeSet.inversePatch)
        assertEquals(verification.expected, gateway.runtimeState.documentState)
        val restored =
            applied.changeSet.inversePatch
                .applyTo(verification.expected.snapshot)
                .requiredSnapshot()
        assertEquals(verification.initial.snapshot, restored)
        assertEquals(HistoryAvailability.UndoAvailable, gateway.runtimeState.historyAvailability)
    }

    private fun measureDensePatchInverse(edge: Int): P2MeasurementMetric {
        val fixture = patchFixture(edge)
        val descriptor = patchDescriptor("p2_patch_inverse_create_dense", edge, "PixelPatch.inverse for C=N")
        return measure(descriptor) {
            P2MeasuredOperation(
                execute = fixture.patch::inverse,
                verify = { inverse ->
                    assertEquals(fixture.patch, inverse.inverse())
                    assertEquals(fixture.expectedRegion, inverse.affectedRegion)
                    assertEquals(fixture.initial, inverse.applyTo(fixture.applied).requiredSnapshot())
                },
                deterministicKey = { patch -> patch.digest() },
            )
        }
    }

    private fun measureDensePatchApply(
        edge: Int,
        inverse: Boolean,
    ): P2MeasurementMetric {
        val fixture = patchFixture(edge)
        val patch = if (inverse) fixture.patch.inverse() else fixture.patch
        val before = if (inverse) fixture.applied else fixture.initial
        val expected = if (inverse) fixture.initial else fixture.applied
        val direction = if (inverse) "inverse" else "forward"
        val descriptor =
            patchDescriptor(
                name = "p2_patch_apply_${direction}_dense",
                edge = edge,
                boundary = "PixelPatch.applyTo $direction for C=N including PixelSurface and next snapshot",
            )
        return measure(descriptor) {
            P2MeasuredOperation(
                execute = { patch.applyTo(before) },
                verify = { result ->
                    assertEquals(fixture.expectedRegion, patch.affectedRegion)
                    assertEquals(expected, result.requiredSnapshot())
                },
                deterministicKey = { result -> result.requiredSnapshot().digest() },
            )
        }
    }

    private fun measureHistoryEntryRetention(
        edge: Int,
        dense: Boolean,
        historyEntries: Int,
    ): P2MeasurementMetric {
        val fixture = historyFixture(edge, dense, historyEntries)
        val kind = if (dense) "dense" else "sparse"
        val descriptor =
            P2MeasurementDescriptor(
                name = "p2_history_retain_${kind}_entry_wrappers",
                workload =
                    P2WorkloadShape(
                        canvas = P2CanvasShape(edge, edge),
                        pathPositions = fixture.changeCount,
                        changeCount = fixture.changeCount,
                        historyEntries = historyEntries,
                    ),
                sampling = P2SamplingPlan(HISTORY_WARMUPS, HISTORY_SAMPLES),
                boundary =
                    "HistoryEntry wrappers over prepared ChangeSets; " +
                        "retained change records are analytical rows",
            )
        return measure(descriptor) {
            P2MeasuredOperation(
                execute = { fixture.appliedResults.map(HistoryEntry::create) },
                verify = { entries -> verifyHistoryEntries(entries, fixture) },
                deterministicKey = { entries -> entries.map { entry -> entry.changeSet.digest() } },
            )
        }
    }

    private fun verifyHistoryEntries(
        entries: List<HistoryEntry>,
        fixture: P2HistoryFixture,
    ) {
        assertEquals(fixture.appliedResults.size, entries.size)
        entries.forEachIndexed { index, entry ->
            val changeSet = fixture.appliedResults[index].changeSet
            assertSame(changeSet, entry.changeSet)
            assertEquals(fixture.changeCount, entry.changeSet.patch.changeCount)
            assertEquals(fixture.expectedRegion, entry.changeSet.renderInvalidation)
            assertEquals(entry.changeSet.patch.inverse(), entry.changeSet.inversePatch)
        }
    }

    private fun historyFixture(
        edge: Int,
        dense: Boolean,
        historyEntries: Int,
    ): P2HistoryFixture {
        val size = canvas(edge, edge)
        val positions = if (dense) densePath(size) else diagonalPath(size, edge)
        val gateway = CommandGateway.create(state(size))
        val results =
            List(historyEntries) { index ->
                val current = gateway.runtimeState.documentState
                val color = if (index % 2 == 0) red else green
                val command = ApplyStrokeCommand.create(current.id, current.revision, stroke(size, positions, color))
                gateway.execute(command).requiredApplied()
            }
        assertEquals(HistoryAvailability.UndoAvailable, gateway.runtimeState.historyAvailability)
        return P2HistoryFixture(results, positions.size, fullCanvasRegion(size))
    }

    private fun patchFixture(edge: Int): P2PatchFixture {
        val size = canvas(edge, edge)
        val initial = snapshot(size)
        val measuredStroke = stroke(size, densePath(size), red)
        val patch = rasterizeStroke(initial, measuredStroke).requiredPatch()
        val applied = appliedSnapshot(patch.applyTo(initial))
        assertEquals(size.pixelCount.toInt(), patch.changeCount)
        return P2PatchFixture(initial, applied, patch, fullCanvasRegion(size))
    }

    private fun analysisRows(): List<P2AnalysisRow> =
        listOf(
            retainedStructure("p2_retained_sparse_h16", DENSE_EDGE, DENSE_EDGE, 16),
            retainedStructure("p2_retained_dense_h8", 64, 64 * 64, 8),
            excludedDense(512),
            excludedDense(2048),
            P2AnalysisRow.ExcludedCandidate(
                descriptor =
                    P2AnalysisDescriptor(
                        name = "p2_candidate_canvas_50000_square",
                        canvas = P2CanvasShape(50_000, 50_000),
                        retained = P2RetainedWorkload(changeCount = 0, historyEntries = 0),
                        boundary =
                            "N=2,500,000,000 exceeds current materialization Int indexability; " +
                                "CanvasSize has no corresponding guard",
                    ),
                reason = P2AnalysisExclusion.PixelCountExceedsListIndexability,
            ),
            P2AnalysisRow.ExcludedCandidate(
                descriptor =
                    P2AnalysisDescriptor(
                        name = "p2_candidate_dense_256_h32",
                        canvas = P2CanvasShape(DENSE_EDGE, DENSE_EDGE),
                        retained =
                            P2RetainedWorkload(
                                changeCount = DENSE_EDGE.toLong() * DENSE_EDGE,
                                historyEntries = 32,
                            ),
                        boundary =
                            "H*C=2,097,152 logical changes and twice that many forward+inverse records",
                    ),
                reason = P2AnalysisExclusion.RetainedChangesExceedWorkerBudget,
            ),
        )

    private fun retainedStructure(
        name: String,
        edge: Int,
        changeCount: Int,
        historyEntries: Int,
    ): P2AnalysisRow.RetainedStructure {
        val totalChanges = changeCount.toLong() * historyEntries
        return P2AnalysisRow.RetainedStructure(
            descriptor =
                P2AnalysisDescriptor(
                    name = name,
                    canvas = P2CanvasShape(edge, edge),
                    retained = P2RetainedWorkload(changeCount.toLong(), historyEntries),
                    boundary =
                        "logical slots and records retained by current snapshot plus H forward/inverse patches; " +
                            "not bytes",
                ),
            counts =
                P2RetainedStructureCounts(
                    snapshotPixelReferenceSlots = edge.toLong() * edge,
                    forwardChangeRecords = totalChanges,
                    inverseChangeRecords = totalChanges,
                ),
        )
    }

    private fun excludedDense(edge: Int): P2AnalysisRow.ExcludedCandidate =
        P2AnalysisRow.ExcludedCandidate(
            descriptor =
                P2AnalysisDescriptor(
                    name = "p2_candidate_dense_${edge}_square",
                    canvas = P2CanvasShape(edge, edge),
                    retained = P2RetainedWorkload(changeCount = edge.toLong() * edge, historyEntries = 1),
                    boundary =
                        "C exceeds executed dense ceiling $MAX_EXECUTED_DENSE_CHANGES " +
                            "under the fixed 512 MiB worker",
                ),
            reason = P2AnalysisExclusion.DenseChangesExceedWorkerBudget,
        )

    private fun snapshotDescriptor(
        name: String,
        edge: Int,
    ): P2MeasurementDescriptor =
        P2MeasurementDescriptor(
            name = name,
            workload =
                P2WorkloadShape(
                    canvas = P2CanvasShape(edge, edge),
                    pathPositions = 0,
                    changeCount = 0,
                    historyEntries = 0,
                ),
            sampling = P2SamplingPlan(STANDARD_WARMUPS, STANDARD_SAMPLES),
            boundary = "PixelSnapshot.create defensive row-major List ownership for N pixels",
        )

    private fun commandDescriptor(
        name: String,
        edge: Int,
        pathPositions: Int,
        boundary: String,
    ): P2MeasurementDescriptor =
        P2MeasurementDescriptor(
            name = name,
            workload =
                P2WorkloadShape(
                    canvas = P2CanvasShape(edge, edge),
                    pathPositions = pathPositions,
                    changeCount = pathPositions,
                    historyEntries = 1,
                ),
            sampling =
                P2SamplingPlan(
                    warmupIterations = if (pathPositions > 16_384) DENSE_WARMUPS else STANDARD_WARMUPS,
                    sampleCount = if (pathPositions > 16_384) DENSE_SAMPLES else STANDARD_SAMPLES,
                ),
            boundary = boundary,
        )

    private fun patchDescriptor(
        name: String,
        edge: Int,
        boundary: String,
    ): P2MeasurementDescriptor =
        P2MeasurementDescriptor(
            name = name,
            workload =
                P2WorkloadShape(
                    canvas = P2CanvasShape(edge, edge),
                    pathPositions = 0,
                    changeCount = edge * edge,
                    historyEntries = 0,
                ),
            sampling = P2SamplingPlan(DENSE_WARMUPS, DENSE_SAMPLES),
            boundary = boundary,
        )

    private fun <T : Any, K : Any> measure(
        descriptor: P2MeasurementDescriptor,
        prepare: () -> P2MeasuredOperation<T, K>,
    ): P2MeasurementMetric = measurementRunner.measure(descriptor, prepare)

    private fun highCardinalityPixels(size: CanvasSize): List<PixelColor> =
        List(size.pixelCount.toInt()) { index ->
            PixelColor.create(
                red = channel(index and 0xff),
                green = channel(index ushr 8 and 0xff),
                blue = channel(index ushr 16 and 0xff),
                alpha = channel(255),
            )
        }

    private fun channel(value: Int): ColorChannel = ColorChannel.create(value).requiredValue()

    private fun densePath(size: CanvasSize): List<PixelPosition> =
        List(size.pixelCount.toInt()) { index ->
            position(index % size.width.value, index / size.width.value)
        }

    private fun diagonalPath(
        size: CanvasSize,
        positions: Int,
    ): List<PixelPosition> =
        List(positions) { coordinate ->
            position(coordinate % size.width.value, coordinate % size.height.value)
        }

    private fun fullCanvasRegion(size: CanvasSize): PixelRegion =
        PixelRegion.create(size, position(0, 0), size).requiredValue()

    private fun PixelPosition.rowMajorIndex(size: CanvasSize): Int = y.value * size.width.value + x.value

    private fun PixelSnapshot.digest(): P2SnapshotDigest = P2SnapshotDigest(size.pixelCount, revision.value, hashCode())

    private fun PixelPatch.digest(): P2PatchDigest =
        P2PatchDigest(changeCount, beforeRevision.value, afterRevision.value, hashCode())

    private fun ChangeSet.digest(): P2PatchDigest = patch.digest()

    private fun CommandResult.requiredApplied(): CommandResult.Applied =
        assertInstanceOf(CommandResult.Applied::class.java, this)

    private fun StrokeRasterizationResult.requiredPatch(): PixelPatch =
        when (this) {
            is StrokeRasterizationResult.Rasterized -> patch
            StrokeRasterizationResult.NoChanges -> fail("Dense measurement stroke unexpectedly made no change")
            is StrokeRasterizationResult.Rejected -> fail("Dense measurement stroke was rejected: $rejection")
        }

    private fun PixelPatchApplicationResult.requiredSnapshot(): PixelSnapshot =
        when (this) {
            is PixelPatchApplicationResult.Applied -> snapshot
            is PixelPatchApplicationResult.Rejected -> fail("Measurement patch application was rejected: $rejection")
        }

    private fun <T> DomainValueResult<T>.requiredValue(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> fail("Measurement value was rejected: $rejection")
        }

    private companion object {
        const val STANDARD_WARMUPS: Int = 5
        const val STANDARD_SAMPLES: Int = 10
        const val DENSE_WARMUPS: Int = 3
        const val DENSE_SAMPLES: Int = 7
        const val HISTORY_WARMUPS: Int = 3
        const val HISTORY_SAMPLES: Int = 7
        const val DENSE_EDGE: Int = 256
        const val MAX_EXECUTED_DENSE_CHANGES: Int = 65_536
        val SNAPSHOT_EDGES: List<Int> = listOf(64, 256, 1024)
        val SPARSE_EDGES: List<Int> = listOf(64, 256, 1024)
        val DENSE_EDGES: List<Int> = listOf(64, 128, 256)
    }
}

private data class P2PatchFixture(
    val initial: PixelSnapshot,
    val applied: PixelSnapshot,
    val patch: PixelPatch,
    val expectedRegion: PixelRegion,
)

private data class P2CommandVerification(
    val initial: DocumentState,
    val expected: DocumentState,
    val expectedChanges: Int,
    val expectedRegion: PixelRegion,
)

private data class P2HistoryFixture(
    val appliedResults: List<CommandResult.Applied>,
    val changeCount: Int,
    val expectedRegion: PixelRegion,
)

private data class P2SnapshotDigest(
    val pixelCount: Long,
    val revision: Long,
    val contentHash: Int,
)

private data class P2PatchDigest(
    val changeCount: Int,
    val beforeRevision: Long,
    val afterRevision: Long,
    val contentHash: Int,
)

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
            if (!bean.isThreadAllocatedMemoryEnabled) {
                bean.isThreadAllocatedMemoryEnabled = true
            }
            return P2ThreadAllocationCounter(bean)
        }
    }
}
