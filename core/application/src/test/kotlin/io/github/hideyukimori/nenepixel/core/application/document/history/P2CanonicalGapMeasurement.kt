package io.github.hideyukimori.nenepixel.core.application.document.history

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability.None
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability.UndoAvailable
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.appliedSnapshot
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.colorAt
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.patch
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.snapshot
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelChange
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatch
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchApplicationRejection
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchApplicationResult
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchCreationResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.fail

internal object P2CanonicalGapMeasurement {
    fun measure(runner: P2HostMeasurementRunner): List<P2MeasurementMetric> =
        listOf(
            measureDuplicateRawStroke(runner),
            measureReferenceClearNoOp(runner),
            measureShuffledPatchCreate(runner),
            measureLatePatchConflict(runner),
        )

    private fun measureDuplicateRawStroke(runner: P2HostMeasurementRunner): P2MeasurementMetric {
        val size = canvas(DENSE_EDGE, DENSE_EDGE)
        val rawPath = densePath(size).flatMap { point -> listOf(point, point) }
        val initial = state(size)
        val expected = state(size, revision(1L), List(size.pixelCount.toInt()) { red })
        val command = ApplyStrokeCommand.create(initial.id, initial.revision, stroke(size, rawPath, red))
        return runner.measure(duplicateDescriptor(size)) {
            val gateway = CommandGateway.create(initial)
            P2MeasuredOperation(
                execute = { gateway.execute(command) },
                verify = { result -> verifyDuplicateResult(result, gateway, initial.snapshot, expected.snapshot) },
                deterministicKey = { result ->
                    result
                        .requiredApplied()
                        .changeSet.patch
                        .digest()
                },
            )
        }
    }

    private fun verifyDuplicateResult(
        result: CommandResult,
        gateway: CommandGateway,
        initial: PixelSnapshot,
        expected: PixelSnapshot,
    ) {
        val applied = result.requiredApplied()
        assertEquals(initial.size.pixelCount.toInt(), applied.changeSet.patch.changeCount)
        assertEquals(fullRegion(initial.size), applied.changeSet.patch.affectedRegion)
        assertEquals(expected, gateway.runtimeState.documentState.snapshot)
        assertEquals(
            initial,
            applied.changeSet.inversePatch
                .applyTo(expected)
                .requiredSnapshot(),
        )
        assertEquals(UndoAvailable, gateway.runtimeState.historyAvailability)
    }

    private fun measureReferenceClearNoOp(runner: P2HostMeasurementRunner): P2MeasurementMetric {
        val size = canvas(DENSE_EDGE, DENSE_EDGE)
        val initial = state(size)
        val command =
            ApplyStrokeCommand.create(
                initial.id,
                initial.revision,
                stroke(size, densePath(size), black),
            )
        return runner.measure(referenceClearNoOpDescriptor(size)) {
            val gateway = CommandGateway.create(initial)
            P2MeasuredOperation(
                execute = { gateway.execute(command) },
                verify = { result -> verifyNoOpResult(result, gateway, initial) },
                deterministicKey = { result -> result },
            )
        }
    }

    private fun verifyNoOpResult(
        result: CommandResult,
        gateway: CommandGateway,
        initial: DocumentState,
    ) {
        val rejected = assertInstanceOf(CommandResult.Rejected::class.java, result)
        assertEquals(RejectionReason.NoEffectiveChange, rejected.reason)
        assertEquals(initial, gateway.runtimeState.documentState)
        assertEquals(None, gateway.runtimeState.historyAvailability)
    }

    private fun measureShuffledPatchCreate(runner: P2HostMeasurementRunner): P2MeasurementMetric {
        val fixture = patchFixture()
        return runner.measure(patchDescriptor("p2_patch_create_shuffled_dense", PATCH_CREATE_BOUNDARY)) {
            P2MeasuredOperation(
                execute = {
                    PixelPatch.create(
                        fixture.initial.size,
                        fixture.initial.revision,
                        fixture.shuffledChanges,
                    )
                },
                verify = { result -> verifyCreatedPatch(result.requiredPatch(), fixture) },
                deterministicKey = { result -> result.requiredPatch().digest() },
            )
        }
    }

    private fun verifyCreatedPatch(
        actual: PixelPatch,
        fixture: P2CanonicalPatchFixture,
    ) {
        assertEquals(fixture.expectedPatch, actual)
        assertEquals(fixture.expectedRegion, actual.affectedRegion)
        val applied = actual.applyTo(fixture.initial).requiredSnapshot()
        assertEquals(fixture.applied, applied)
        assertEquals(fixture.initial, actual.inverse().applyTo(applied).requiredSnapshot())
    }

    private fun measureLatePatchConflict(runner: P2HostMeasurementRunner): P2MeasurementMetric {
        val fixture = patchFixture()
        val lastIndex =
            fixture.initial.size.pixelCount
                .toInt() - 1
        val conflictPixels =
            List(
                fixture.initial.size.pixelCount
                    .toInt(),
            ) { index ->
                if (index == lastIndex) green else black
            }
        val conflicted = snapshot(fixture.initial.size, pixels = conflictPixels)
        val expectedUnchanged = snapshot(fixture.initial.size, pixels = conflictPixels)
        return runner.measure(patchDescriptor("p2_patch_apply_late_conflict_dense", LATE_CONFLICT_BOUNDARY)) {
            P2MeasuredOperation(
                execute = { fixture.expectedPatch.applyTo(conflicted) },
                verify = { result -> verifyLateConflict(result, conflicted, expectedUnchanged) },
                deterministicKey = { result -> P2ConflictDigest(result, conflicted.hashCode()) },
            )
        }
    }

    private fun verifyLateConflict(
        result: PixelPatchApplicationResult,
        conflicted: PixelSnapshot,
        expectedUnchanged: PixelSnapshot,
    ) {
        val rejected = assertInstanceOf(PixelPatchApplicationResult.Rejected::class.java, result)
        val mismatch =
            assertInstanceOf(
                PixelPatchApplicationRejection.BeforeValueMismatch::class.java,
                rejected.rejection,
            )
        val expectedPosition = position(DENSE_EDGE - 1, DENSE_EDGE - 1)
        assertEquals(expectedPosition, mismatch.position)
        assertEquals(black, mismatch.expected)
        assertEquals(green, mismatch.actual)
        assertEquals(green, colorAt(conflicted, expectedPosition))
        assertEquals(expectedUnchanged, conflicted)
    }

    private fun patchFixture(): P2CanonicalPatchFixture {
        val size = canvas(DENSE_EDGE, DENSE_EDGE)
        val initial = snapshot(size)
        val forwardChanges = densePath(size).map { point -> PixelChange.create(point, black, red) }
        val expectedPatch = patch(size, initial.revision, forwardChanges)
        return P2CanonicalPatchFixture(
            initial = initial,
            applied = appliedSnapshot(expectedPatch.applyTo(initial)),
            expectedPatch = expectedPatch,
            shuffledChanges = forwardChanges.reversed(),
            expectedRegion = fullRegion(size),
        )
    }

    private fun duplicateDescriptor(size: CanvasSize): P2MeasurementDescriptor =
        P2MeasurementDescriptor(
            name = "p2_command_dense_duplicate_raw_path",
            workload =
                workload(
                    size,
                    pathPositions = size.pixelCount.toInt() * 2,
                    changeCount = size.pixelCount.toInt(),
                    historyEntries = 1,
                ),
            sampling = DENSE_SAMPLING,
            boundary =
                "CommandGateway.execute duplicated full-canvas raw stroke; " +
                    "duplicate raw positions collapse before canonical patch creation",
        )

    private fun referenceClearNoOpDescriptor(size: CanvasSize): P2MeasurementDescriptor =
        P2MeasurementDescriptor(
            name = "p2_command_dense_reference_clear_noop",
            workload = workload(size, pathPositions = size.pixelCount.toInt(), changeCount = 0, historyEntries = 0),
            sampling = DENSE_SAMPLING,
            boundary =
                "CommandGateway.execute black-on-black reference-clear fixture; typed no-effective-change; " +
                    "not semantic blank or eraser selection",
        )

    private fun patchDescriptor(
        name: String,
        boundary: String,
    ): P2MeasurementDescriptor =
        P2MeasurementDescriptor(
            name = name,
            workload = workload(canvas(DENSE_EDGE, DENSE_EDGE), 0, DENSE_PIXEL_COUNT, historyEntries = 0),
            sampling = DENSE_SAMPLING,
            boundary = boundary,
        )

    private fun workload(
        size: CanvasSize,
        pathPositions: Int,
        changeCount: Int,
        historyEntries: Int,
    ): P2WorkloadShape =
        P2WorkloadShape(
            canvas = P2CanvasShape(size.width.value, size.height.value),
            pathPositions = pathPositions,
            changeCount = changeCount,
            historyEntries = historyEntries,
        )

    private fun densePath(size: CanvasSize): List<PixelPosition> =
        List(size.pixelCount.toInt()) { index ->
            position(index % size.width.value, index / size.width.value)
        }

    private fun fullRegion(size: CanvasSize): PixelRegion =
        PixelRegion.create(size, position(0, 0), size).requiredValue()

    private fun CommandResult.requiredApplied(): CommandResult.Applied =
        assertInstanceOf(CommandResult.Applied::class.java, this)

    private fun PixelPatchCreationResult.requiredPatch(): PixelPatch =
        when (this) {
            is PixelPatchCreationResult.Created -> patch
            is PixelPatchCreationResult.Rejected -> fail("Canonical gap patch was rejected: $rejection")
        }

    private fun PixelPatchApplicationResult.requiredSnapshot(): PixelSnapshot =
        when (this) {
            is PixelPatchApplicationResult.Applied -> snapshot
            is PixelPatchApplicationResult.Rejected -> fail("Canonical gap application was rejected: $rejection")
        }

    private fun PixelPatch.digest(): P2CanonicalPatchDigest =
        P2CanonicalPatchDigest(changeCount, beforeRevision.value, afterRevision.value, hashCode())

    private fun <T> DomainValueResult<T>.requiredValue(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> fail("Canonical gap value was rejected: $rejection")
        }

    private const val DENSE_EDGE: Int = 256
    private const val DENSE_PIXEL_COUNT: Int = DENSE_EDGE * DENSE_EDGE
    private val DENSE_SAMPLING: P2SamplingPlan = P2SamplingPlan(warmupIterations = 3, sampleCount = 7)
    private const val PATCH_CREATE_BOUNDARY: String =
        "PixelPatch.create from reverse-row-major dense changes; canonical sort and invariant checks included"
    private const val LATE_CONFLICT_BOUNDARY: String =
        "PixelPatch.applyTo dense patch with before-value mismatch at final canonical position; typed atomic reject"
}

private data class P2CanonicalPatchFixture(
    val initial: PixelSnapshot,
    val applied: PixelSnapshot,
    val expectedPatch: PixelPatch,
    val shuffledChanges: List<PixelChange>,
    val expectedRegion: PixelRegion,
)

private data class P2CanonicalPatchDigest(
    val changeCount: Int,
    val beforeRevision: Long,
    val afterRevision: Long,
    val contentHash: Int,
)

private data class P2ConflictDigest(
    val result: PixelPatchApplicationResult,
    val unchangedSnapshotHash: Int,
)
