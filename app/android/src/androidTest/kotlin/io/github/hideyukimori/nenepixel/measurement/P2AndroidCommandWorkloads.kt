package io.github.hideyukimori.nenepixel.measurement

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.DocumentCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.RedoCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.document.command.UndoCommand
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame

internal enum class P2CommandWorkloadKind(
    val metricName: String,
) {
    SparseApply("sparse_apply_stroke"),
    DenseApply("dense_apply_stroke"),
    DenseNoOp("dense_same_color_no_op"),
    DenseUndo("dense_undo"),
    DenseRedo("dense_redo"),
}

internal data class P2CommandWorkloadSpec(
    val kind: P2CommandWorkloadKind,
    val canvasWidth: Int,
    val canvasHeight: Int,
) {
    val positionCount: Int
        get() =
            if (kind == P2CommandWorkloadKind.SparseApply) {
                minOf(canvasWidth, canvasHeight)
            } else {
                canvasWidth * canvasHeight
            }
}

internal object P2CommandWorkloadCatalog {
    private val CANVAS_EDGES: List<Int> = listOf(16, 64, 256)

    val specs: List<P2CommandWorkloadSpec> =
        CANVAS_EDGES.flatMap(::squareSpecs)

    fun squareSpecs(edge: Int): List<P2CommandWorkloadSpec> {
        require(edge > 0) { "A square workload edge must be positive." }
        return shapeSpecs(edge, edge)
    }

    fun shapeSpecs(
        width: Int,
        height: Int,
    ): List<P2CommandWorkloadSpec> {
        require(width > 0 && height > 0) { "Workload width and height must be positive." }
        return P2CommandWorkloadKind.entries.map { kind -> P2CommandWorkloadSpec(kind, width, height) }
    }
}

internal data class CommandOutcomeDescriptor(
    val resultKind: String,
    val documentHash: Int,
    val snapshotHash: Int,
    val revision: Long,
    val history: String,
    val changeSetBeforeRevision: Long?,
    val changeSetAfterRevision: Long?,
    val renderInvalidation: P2CommandRegionDescriptor?,
    val unchangedStateIdentity: Boolean,
)

internal data class P2CommandRegionDescriptor(
    val originX: Int,
    val originY: Int,
    val width: Int,
    val height: Int,
)

internal data class P2CommandResultDescriptor(
    val resultKind: String,
    val changeSetBeforeRevision: Long?,
    val changeSetAfterRevision: Long?,
    val renderInvalidation: P2CommandRegionDescriptor?,
)

internal class PreparedCommandWorkload internal constructor(
    private val gateway: CommandGateway,
    private var command: DocumentCommand?,
    private var expectedState: DocumentState?,
    private val expectedResult: ExpectedCommandResult,
    private val expectedBeforeRevision: Long,
    private val expectedAfterRevision: Long,
    private val expectedHistory: HistoryAvailability,
    private val expectedRenderInvalidation: PixelRegion?,
    private val expectSameStateInstance: Boolean,
) {
    fun execute(): CommandResult {
        val nextCommand = requireNotNull(command) { "A measurement command may execute only once." }
        command = null
        return gateway.execute(nextCommand)
    }

    fun verify(result: CommandResult): CommandOutcomeDescriptor {
        val expectedDocument = requireNotNull(expectedState)
        val runtimeState = gateway.runtimeState
        assertEquals(expectedDocument, runtimeState.documentState)
        if (expectSameStateInstance) assertSame(expectedDocument, runtimeState.documentState)
        assertEquals(expectedAfterRevision, runtimeState.documentState.revision.value)
        assertEquals(expectedHistory, runtimeState.historyAvailability)
        val resultDescriptor =
            expectedResult.verify(
                result,
                P2ExpectedCommandTransition(
                    beforeRevision = expectedBeforeRevision,
                    afterRevision = expectedAfterRevision,
                    renderInvalidation = expectedRenderInvalidation,
                ),
            )
        expectedState = null
        return CommandOutcomeDescriptor(
            resultKind = resultDescriptor.resultKind,
            documentHash = runtimeState.documentState.hashCode(),
            snapshotHash = runtimeState.documentState.snapshot.hashCode(),
            revision = runtimeState.documentState.revision.value,
            history = runtimeState.historyAvailability.csvName(),
            changeSetBeforeRevision = resultDescriptor.changeSetBeforeRevision,
            changeSetAfterRevision = resultDescriptor.changeSetAfterRevision,
            renderInvalidation = resultDescriptor.renderInvalidation,
            unchangedStateIdentity = runtimeState.documentState === expectedDocument,
        )
    }

    companion object {
        fun create(spec: P2CommandWorkloadSpec): PreparedCommandWorkload =
            when (spec.kind) {
                P2CommandWorkloadKind.SparseApply -> WorkloadFactory.apply(spec, sparse = true)
                P2CommandWorkloadKind.DenseApply -> WorkloadFactory.apply(spec, sparse = false)
                P2CommandWorkloadKind.DenseNoOp -> WorkloadFactory.noOp(spec)
                P2CommandWorkloadKind.DenseUndo -> WorkloadFactory.undo(spec)
                P2CommandWorkloadKind.DenseRedo -> WorkloadFactory.redo(spec)
            }
    }
}

internal enum class ExpectedCommandResult {
    Applied,
    NoEffectiveChange,
    ;

    fun verify(
        result: CommandResult,
        expected: P2ExpectedCommandTransition,
    ): P2CommandResultDescriptor =
        when (this) {
            Applied -> result.requireApplied(expected)
            NoEffectiveChange -> result.requireNoEffectiveChange()
        }
}

internal data class P2ExpectedCommandTransition(
    val beforeRevision: Long,
    val afterRevision: Long,
    val renderInvalidation: PixelRegion?,
)

private object WorkloadFactory {
    fun apply(
        spec: P2CommandWorkloadSpec,
        sparse: Boolean,
    ): PreparedCommandWorkload {
        val values = CoreMeasurementValues(spec.canvasWidth, spec.canvasHeight)
        val initial = values.document(Revision.initial(), values.whitePixels())
        val path = if (sparse) values.diagonalPath() else values.densePath()
        val expectedPixels = if (sparse) values.diagonalRedPixels() else values.redPixels()
        val gateway = CommandGateway.create(initial)
        return prepared(
            gateway = gateway,
            command = values.applyCommand(initial, path),
            expectedState = values.document(values.revision(1L), expectedPixels),
            expectedResult = ExpectedCommandResult.Applied,
            beforeRevision = 0L,
            afterRevision = 1L,
            history = HistoryAvailability.UndoAvailable,
            renderInvalidation = if (sparse) values.diagonalRegion() else values.fullRegion(),
        )
    }

    fun noOp(spec: P2CommandWorkloadSpec): PreparedCommandWorkload {
        val values = CoreMeasurementValues(spec.canvasWidth, spec.canvasHeight)
        val initial = values.document(Revision.initial(), values.redPixels())
        val gateway = CommandGateway.create(initial)
        return prepared(
            gateway = gateway,
            command = values.applyCommand(initial, values.densePath()),
            expectedState = initial,
            expectedResult = ExpectedCommandResult.NoEffectiveChange,
            beforeRevision = 0L,
            afterRevision = 0L,
            history = HistoryAvailability.None,
            renderInvalidation = null,
            expectSameStateInstance = true,
        )
    }

    fun undo(spec: P2CommandWorkloadSpec): PreparedCommandWorkload {
        val values = CoreMeasurementValues(spec.canvasWidth, spec.canvasHeight)
        val initial = values.document(Revision.initial(), values.whitePixels())
        val gateway = CommandGateway.create(initial)
        gateway
            .execute(values.applyCommand(initial, values.densePath()))
            .requireApplied(P2ExpectedCommandTransition(0L, 1L, values.fullRegion()))
        val applied = gateway.runtimeState.documentState
        return prepared(
            gateway = gateway,
            command = UndoCommand.create(applied.id, applied.revision),
            expectedState = initial,
            expectedResult = ExpectedCommandResult.Applied,
            beforeRevision = 1L,
            afterRevision = 0L,
            history = HistoryAvailability.RedoAvailable,
            renderInvalidation = values.fullRegion(),
        )
    }

    fun redo(spec: P2CommandWorkloadSpec): PreparedCommandWorkload {
        val values = CoreMeasurementValues(spec.canvasWidth, spec.canvasHeight)
        val initial = values.document(Revision.initial(), values.whitePixels())
        val gateway = CommandGateway.create(initial)
        gateway
            .execute(values.applyCommand(initial, values.densePath()))
            .requireApplied(P2ExpectedCommandTransition(0L, 1L, values.fullRegion()))
        val applied = gateway.runtimeState.documentState
        gateway
            .execute(UndoCommand.create(applied.id, applied.revision))
            .requireApplied(P2ExpectedCommandTransition(1L, 0L, values.fullRegion()))
        val undone = gateway.runtimeState.documentState
        return prepared(
            gateway = gateway,
            command = RedoCommand.create(undone.id, undone.revision),
            expectedState = applied,
            expectedResult = ExpectedCommandResult.Applied,
            beforeRevision = 0L,
            afterRevision = 1L,
            history = HistoryAvailability.UndoAvailable,
            renderInvalidation = values.fullRegion(),
        )
    }

    private fun prepared(
        gateway: CommandGateway,
        command: DocumentCommand,
        expectedState: DocumentState,
        expectedResult: ExpectedCommandResult,
        beforeRevision: Long,
        afterRevision: Long,
        history: HistoryAvailability,
        renderInvalidation: PixelRegion?,
        expectSameStateInstance: Boolean = false,
    ): PreparedCommandWorkload =
        PreparedCommandWorkload(
            gateway,
            command,
            expectedState,
            expectedResult,
            beforeRevision,
            afterRevision,
            history,
            renderInvalidation,
            expectSameStateInstance,
        )
}

private class CoreMeasurementValues(
    canvasWidth: Int,
    canvasHeight: Int,
) {
    private val canvas: CanvasSize =
        CanvasSize.create(
            CanvasWidth.create(canvasWidth).requiredValue(),
            CanvasHeight.create(canvasHeight).requiredValue(),
        )
    private val documentId: DocumentId = DocumentId.create(DOCUMENT_ID).requiredValue()
    private val white: PixelColor = color(CHANNEL_MAX, CHANNEL_MAX, CHANNEL_MAX, CHANNEL_MAX)
    private val red: PixelColor = color(CHANNEL_MAX, CHANNEL_MIN, CHANNEL_MIN, CHANNEL_MAX)

    fun whitePixels(): List<PixelColor> = List(canvas.pixelCount.toInt()) { white }

    fun redPixels(): List<PixelColor> = List(canvas.pixelCount.toInt()) { red }

    fun diagonalRedPixels(): List<PixelColor> =
        List(canvas.pixelCount.toInt()) { index ->
            if (index % canvas.width.value == index / canvas.width.value) red else white
        }

    fun diagonalPath(): List<PixelPosition> =
        List(minOf(canvas.width.value, canvas.height.value)) { coordinate -> position(coordinate, coordinate) }

    fun densePath(): List<PixelPosition> =
        List(canvas.pixelCount.toInt()) { index ->
            position(index % canvas.width.value, index / canvas.width.value)
        }

    fun document(
        revision: Revision,
        pixels: List<PixelColor>,
    ): DocumentState =
        DocumentState.create(
            documentId,
            PixelSnapshot.create(canvas, revision, pixels).requiredValue(),
        )

    fun applyCommand(
        state: DocumentState,
        path: List<PixelPosition>,
    ): ApplyStrokeCommand =
        ApplyStrokeCommand.create(
            state.id,
            state.revision,
            Stroke.create(canvas, path, StrokeEffect.Paint(red)).requiredValue(),
        )

    fun revision(value: Long): Revision = Revision.create(value).requiredValue()

    fun fullRegion(): PixelRegion = PixelRegion.create(canvas, position(0, 0), canvas).requiredValue()

    fun diagonalRegion(): PixelRegion {
        val edge = minOf(canvas.width.value, canvas.height.value)
        val size =
            CanvasSize.create(
                CanvasWidth.create(edge).requiredValue(),
                CanvasHeight.create(edge).requiredValue(),
            )
        return PixelRegion.create(canvas, position(0, 0), size).requiredValue()
    }

    private fun position(
        x: Int,
        y: Int,
    ): PixelPosition =
        PixelPosition.create(
            PixelX.create(x).requiredValue(),
            PixelY.create(y).requiredValue(),
        )

    private fun color(
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ): PixelColor =
        PixelColor.create(
            ColorChannel.create(red).requiredValue(),
            ColorChannel.create(green).requiredValue(),
            ColorChannel.create(blue).requiredValue(),
            ColorChannel.create(alpha).requiredValue(),
        )

    private companion object {
        const val DOCUMENT_ID: String = "33333333333333333333333333333333"
        const val CHANNEL_MIN: Int = 0
        const val CHANNEL_MAX: Int = 255
    }
}

private fun CommandResult.requireApplied(expected: P2ExpectedCommandTransition): P2CommandResultDescriptor =
    when (this) {
        is CommandResult.Applied -> {
            assertEquals(expected.beforeRevision, changeSet.beforeRevision.value)
            assertEquals(expected.afterRevision, changeSet.afterRevision.value)
            assertEquals(requireNotNull(expected.renderInvalidation), changeSet.renderInvalidation)
            P2CommandResultDescriptor(
                resultKind = "applied",
                changeSetBeforeRevision = changeSet.beforeRevision.value,
                changeSetAfterRevision = changeSet.afterRevision.value,
                renderInvalidation = changeSet.renderInvalidation.descriptor(),
            )
        }

        is CommandResult.Rejected -> {
            measurementFailure("Expected applied command but was rejected: $reason")
        }

        is CommandResult.Failed -> {
            measurementFailure("Expected applied command but failed: $failure")
        }
    }

private fun CommandResult.requireNoEffectiveChange(): P2CommandResultDescriptor =
    when (this) {
        is CommandResult.Rejected -> {
            assertEquals(RejectionReason.NoEffectiveChange, reason)
            P2CommandResultDescriptor(
                resultKind = "rejected_no_effective_change",
                changeSetBeforeRevision = null,
                changeSetAfterRevision = null,
                renderInvalidation = null,
            )
        }

        is CommandResult.Applied -> {
            measurementFailure("Expected no-op rejection but command was applied: $changeSet")
        }

        is CommandResult.Failed -> {
            measurementFailure("Expected no-op rejection but command failed: $failure")
        }
    }

private fun PixelRegion.descriptor(): P2CommandRegionDescriptor =
    P2CommandRegionDescriptor(
        originX = origin.x.value,
        originY = origin.y.value,
        width = size.width.value,
        height = size.height.value,
    )

private fun HistoryAvailability.csvName(): String =
    when (this) {
        HistoryAvailability.None -> "none"
        HistoryAvailability.UndoAvailable -> "undo_available"
        HistoryAvailability.RedoAvailable -> "redo_available"
        HistoryAvailability.UndoAndRedoAvailable -> "undo_and_redo_available"
    }

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> measurementFailure("Measurement fixture was rejected: $rejection")
    }

private fun measurementFailure(message: String): Nothing = throw AssertionError(message)
