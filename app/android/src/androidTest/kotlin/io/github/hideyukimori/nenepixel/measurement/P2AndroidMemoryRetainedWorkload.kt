package io.github.hideyukimori.nenepixel.measurement

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.document.transition.ChangeSet
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.editor.P2RetainedProjectionMeasurementOwner
import io.github.hideyukimori.nenepixel.presentation.compose.editor.retainP2ProjectionForMeasurement

internal data class P2AndroidMemoryRetainedOwner(
    val finalDocumentState: DocumentState,
    val changeSets: List<ChangeSet>,
    val projection: P2RetainedProjectionMeasurementOwner,
)

internal data class P2AndroidMemoryCorrectness(
    val finalPixelDigestSha256: String,
    val entryDescriptorDigestSha256: String,
)

internal data class P2AndroidMemoryPreparedRetainedState(
    val owner: P2AndroidMemoryRetainedOwner,
    val correctness: P2AndroidMemoryCorrectness,
)

internal object P2AndroidMemoryRetainedWorkload {
    fun preload() {
        val values = P2MemoryFixtureValues(PRELOAD_CANVAS_EDGE)
        val initial = values.document(Revision.initial(), values.whitePixels())
        val gateway = CommandGateway.create(initial)
        val command = values.command(initial, listOf(memoryPosition(0, 0)), values.red)
        val applied = gateway.execute(command).requiredApplied()
        check(applied.changeSet.beforeRevision.value == 0L)
        check(applied.changeSet.afterRevision.value == 1L)
        val projection = retainP2ProjectionForMeasurement(gateway.runtimeState.documentState.snapshot)
        check(projection.pixelCount == PRELOAD_CANVAS_EDGE * PRELOAD_CANVAS_EDGE)
        check(projection.firstArgb == P2AndroidMemoryProtocol.OPAQUE_RED_ARGB)
    }

    fun prepareAndVerify(): P2AndroidMemoryRetainedOwner {
        val values = P2MemoryFixtureValues(P2AndroidMemoryProtocol.CANVAS_EDGE)
        val initial = values.document(Revision.initial(), values.whitePixels())
        val gateway = CommandGateway.create(initial)
        val expectedPixels = values.whitePixels().toMutableList()
        val changeSets = ArrayList<ChangeSet>(P2AndroidMemoryProtocol.HISTORY_ENTRIES)
        repeat(P2AndroidMemoryProtocol.HISTORY_ENTRIES) { entryIndex ->
            executeEntry(values, gateway, expectedPixels, entryIndex).also(changeSets::add)
        }
        val finalState = gateway.runtimeState.documentState
        verifyFinalState(finalState, expectedPixels, changeSets)
        val projection = retainP2ProjectionForMeasurement(finalState.snapshot)
        verifyProjection(projection)
        val retainedChangeSets = changeSets.toList()
        val owner = P2AndroidMemoryRetainedOwner(finalState, retainedChangeSets, projection)
        verifyCorrectness(owner)
        return owner
    }

    fun reportState(owner: P2AndroidMemoryRetainedOwner): P2AndroidMemoryPreparedRetainedState =
        P2AndroidMemoryPreparedRetainedState(
            owner = owner,
            correctness = correctness(owner),
        )

    private fun verifyCorrectness(owner: P2AndroidMemoryRetainedOwner) {
        val correctness = correctness(owner)
        check(correctness.finalPixelDigestSha256 == P2AndroidMemoryProtocol.EXPECTED_PROJECTION_DIGEST)
        check(correctness.entryDescriptorDigestSha256 == P2AndroidMemoryProtocol.EXPECTED_ENTRY_DESCRIPTOR_DIGEST)
    }

    private fun correctness(owner: P2AndroidMemoryRetainedOwner): P2AndroidMemoryCorrectness =
        P2AndroidMemoryCorrectness(
            finalPixelDigestSha256 = P2AndroidMemoryDigest.finalPixels(owner.finalDocumentState.snapshot),
            entryDescriptorDigestSha256 = P2AndroidMemoryDigest.entryDescriptors(owner.changeSets),
        )

    private fun executeEntry(
        values: P2MemoryFixtureValues,
        gateway: CommandGateway,
        expectedPixels: MutableList<PixelColor>,
        entryIndex: Int,
    ): ChangeSet {
        val expectation = P2AndroidMemoryValues.entryExpectation(entryIndex)
        val target = if (expectation.targetArgb == P2AndroidMemoryProtocol.OPAQUE_RED_ARGB) values.red else values.white
        val positions = values.blockPositions(expectation.blockIndex)
        val current = gateway.runtimeState.documentState
        val applied = gateway.execute(values.command(current, positions, target)).requiredApplied()
        val expectedRegion = values.blockRegion(expectation.blockIndex)
        verifyEntry(applied.changeSet, expectedRegion, entryIndex)
        positions.forEach { position -> expectedPixels[values.indexOf(position)] = target }
        val runtime = gateway.runtimeState
        check(runtime.documentState.revision.value == entryIndex + 1L)
        check(runtime.historyAvailability == HistoryAvailability.UndoAvailable)
        verifyPixels(runtime.documentState.snapshot, expectedPixels)
        return applied.changeSet
    }

    private fun verifyEntry(
        changeSet: ChangeSet,
        expectedRegion: PixelRegion,
        entryIndex: Int,
    ) {
        check(changeSet.beforeRevision.value == entryIndex.toLong())
        check(changeSet.afterRevision.value == entryIndex + 1L)
        check(changeSet.renderInvalidation == expectedRegion)
    }

    private fun verifyFinalState(
        state: DocumentState,
        expectedPixels: List<PixelColor>,
        changeSets: List<ChangeSet>,
    ) {
        check(state.revision.value == P2AndroidMemoryProtocol.FINAL_REVISION)
        check(changeSets.size == P2AndroidMemoryProtocol.HISTORY_ENTRIES)
        check(expectedPixels.all { color -> color == P2MemoryFixtureValues.white })
        verifyPixels(state.snapshot, expectedPixels)
    }

    private fun verifyPixels(
        snapshot: PixelSnapshot,
        expectedPixels: List<PixelColor>,
    ) {
        check(snapshot.size.pixelCount == expectedPixels.size.toLong())
        expectedPixels.indices.forEach { index ->
            check(snapshot.colorAt(P2AndroidMemoryValues.positionAt(index)).memoryValue() == expectedPixels[index]) {
                "Retained-memory pixel mismatch at row-major index $index."
            }
        }
    }

    private fun verifyProjection(projection: P2RetainedProjectionMeasurementOwner) {
        check(projection.pixelCount == P2AndroidMemoryProtocol.PIXEL_COUNT)
        check(projection.firstX == 0 && projection.firstY == 0)
        check(projection.lastX == P2AndroidMemoryProtocol.CANVAS_EDGE - 1)
        check(projection.lastY == P2AndroidMemoryProtocol.CANVAS_EDGE - 1)
        check(projection.firstArgb == P2AndroidMemoryProtocol.OPAQUE_WHITE_ARGB)
        check(projection.lastArgb == P2AndroidMemoryProtocol.OPAQUE_WHITE_ARGB)
        check(projection.mismatchCount(P2AndroidMemoryProtocol.OPAQUE_WHITE_ARGB) == 0)
        check(projection.projectionDigestSha256 == P2AndroidMemoryProtocol.EXPECTED_PROJECTION_DIGEST)
    }

    private const val PRELOAD_CANVAS_EDGE: Int = 16
}

internal class P2MemoryFixtureValues(
    private val canvasEdge: Int,
) {
    val red: PixelColor = color(CHANNEL_MAX, CHANNEL_MIN, CHANNEL_MIN, CHANNEL_MAX)
    val white: PixelColor = Companion.white
    private val canvas: CanvasSize =
        CanvasSize.create(
            CanvasWidth.create(canvasEdge).memoryValue(),
            CanvasHeight.create(canvasEdge).memoryValue(),
        )
    private val documentId: DocumentId = DocumentId.create(DOCUMENT_ID).memoryValue()

    fun whitePixels(): List<PixelColor> = List(canvas.pixelCount.toInt()) { white }

    fun blockPositions(blockIndex: Int): List<PixelPosition> {
        val rowOffset = blockIndex * P2AndroidMemoryProtocol.BLOCK_HEIGHT
        return List(P2AndroidMemoryProtocol.CHANGE_COUNT_PER_ENTRY) { index ->
            memoryPosition(index % canvasEdge, rowOffset + index / canvasEdge)
        }
    }

    fun blockRegion(blockIndex: Int): PixelRegion =
        PixelRegion
            .create(
                canvas,
                memoryPosition(0, blockIndex * P2AndroidMemoryProtocol.BLOCK_HEIGHT),
                CanvasSize.create(
                    CanvasWidth.create(P2AndroidMemoryProtocol.CANVAS_EDGE).memoryValue(),
                    CanvasHeight.create(P2AndroidMemoryProtocol.BLOCK_HEIGHT).memoryValue(),
                ),
            ).memoryValue()

    fun indexOf(position: PixelPosition): Int = position.y.value * canvasEdge + position.x.value

    fun document(
        revision: Revision,
        pixels: List<PixelColor>,
    ): DocumentState =
        DocumentState.create(
            documentId,
            PixelSnapshot.create(canvas, revision, pixels).memoryValue(),
        )

    fun command(
        state: DocumentState,
        positions: List<PixelPosition>,
        target: PixelColor,
    ): ApplyStrokeCommand =
        ApplyStrokeCommand.create(
            state.id,
            state.revision,
            Stroke.create(canvas, positions, target).memoryValue(),
        )

    companion object {
        val white: PixelColor = color(CHANNEL_MAX, CHANNEL_MAX, CHANNEL_MAX, CHANNEL_MAX)
        private const val DOCUMENT_ID: String = "55555555555555555555555555555555"
        private const val CHANNEL_MIN: Int = 0
        private const val CHANNEL_MAX: Int = 255

        private fun color(
            red: Int,
            green: Int,
            blue: Int,
            alpha: Int,
        ): PixelColor =
            PixelColor.create(
                ColorChannel.create(red).memoryValue(),
                ColorChannel.create(green).memoryValue(),
                ColorChannel.create(blue).memoryValue(),
                ColorChannel.create(alpha).memoryValue(),
            )
    }
}

internal fun memoryPosition(
    x: Int,
    y: Int,
): PixelPosition = PixelPosition.create(PixelX.create(x).memoryValue(), PixelY.create(y).memoryValue())

internal fun <T> DomainValueResult<T>.memoryValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> throw AssertionError("Retained-memory fixture rejected: $rejection")
    }

private fun CommandResult.requiredApplied(): CommandResult.Applied =
    this as? CommandResult.Applied ?: throw AssertionError("Expected applied retained-memory command: $this")
