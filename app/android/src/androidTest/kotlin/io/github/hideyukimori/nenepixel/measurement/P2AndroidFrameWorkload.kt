package io.github.hideyukimori.nenepixel.measurement

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.UndoCommand
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReducer
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceState
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
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorRenderState
import io.github.hideyukimori.nenepixel.presentation.compose.editor.FixedSliceEditorController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

internal class P2AndroidFrameWorkload private constructor(
    val controller: FixedSliceEditorController,
    private val gateway: CommandGateway,
    private val stroke: Stroke,
    private val initialDocument: DocumentState,
    private val expectedDocument: DocumentState,
) {
    val canvasEdge: Int
        get() = initialDocument.size.width.value

    val positionCount: Int
        get() = canvasEdge * canvasEdge

    val expectedRenderedArgb: Int
        get() = EXPECTED_RENDERED_ARGB

    fun createCommand(): ApplyStrokeCommand {
        val current = gateway.runtimeState.documentState
        assertEquals(initialDocument, current)
        return ApplyStrokeCommand.create(current.id, current.revision, stroke)
    }

    fun execute(command: ApplyStrokeCommand): CommandResult = gateway.execute(command)

    fun verifyApplied(result: CommandResult): EditorRenderState {
        val applied = result as? CommandResult.Applied ?: frameFailure("Expected applied frame command: $result")
        assertEquals(0L, applied.changeSet.beforeRevision.value)
        assertEquals(1L, applied.changeSet.afterRevision.value)
        assertEquals(expectedDocument, gateway.runtimeState.documentState)
        assertEquals(HistoryAvailability.UndoAvailable, gateway.runtimeState.historyAvailability)
        val renderState = controller.renderState
        assertEquals(expectedDocument.snapshot, renderState.snapshot)
        assertTrue(renderState.canUndo)
        return renderState
    }

    fun resetAndVerify(): EditorRenderState {
        val current = gateway.runtimeState.documentState
        val result = gateway.execute(UndoCommand.create(current.id, current.revision))
        val applied = result as? CommandResult.Applied ?: frameFailure("Expected applied frame reset: $result")
        assertEquals(1L, applied.changeSet.beforeRevision.value)
        assertEquals(0L, applied.changeSet.afterRevision.value)
        assertEquals(initialDocument, gateway.runtimeState.documentState)
        assertEquals(HistoryAvailability.RedoAvailable, gateway.runtimeState.historyAvailability)
        return controller.renderState
    }

    companion object {
        fun create(): P2AndroidFrameWorkload {
            val values = FrameValues(CANVAS_EDGE)
            val initial = values.document(Revision.initial(), values.whitePixels())
            val expected = values.document(values.revision(1L), values.redPixels())
            val gateway = CommandGateway.create(initial)
            val controller =
                FixedSliceEditorController.create(
                    commandGateway = gateway,
                    workspaceReducer = WorkspaceReducer.create(),
                    initialWorkspaceState = WorkspaceState.create(values.red, values.canvas),
                )
            return P2AndroidFrameWorkload(
                controller = controller,
                gateway = gateway,
                stroke = values.denseStroke(),
                initialDocument = initial,
                expectedDocument = expected,
            )
        }

        private const val CANVAS_EDGE: Int = 256
        private const val EXPECTED_RENDERED_ARGB: Int = -0x10000
    }
}

private class FrameValues(
    canvasEdge: Int,
) {
    val canvas: CanvasSize =
        CanvasSize.create(
            CanvasWidth.create(canvasEdge).frameValue(),
            CanvasHeight.create(canvasEdge).frameValue(),
        )
    val red: PixelColor = color(CHANNEL_MAX, CHANNEL_MIN, CHANNEL_MIN, CHANNEL_MAX)
    private val white: PixelColor = color(CHANNEL_MAX, CHANNEL_MAX, CHANNEL_MAX, CHANNEL_MAX)
    private val documentId: DocumentId = DocumentId.create(DOCUMENT_ID).frameValue()

    fun whitePixels(): List<PixelColor> = List(canvas.pixelCount.toInt()) { white }

    fun redPixels(): List<PixelColor> = List(canvas.pixelCount.toInt()) { red }

    fun denseStroke(): Stroke =
        Stroke
            .create(
                canvas,
                List(canvas.pixelCount.toInt()) { index ->
                    position(index % canvas.width.value, index / canvas.width.value)
                },
                red,
            ).frameValue()

    fun document(
        revision: Revision,
        pixels: List<PixelColor>,
    ): DocumentState =
        DocumentState.create(
            documentId,
            PixelSnapshot.create(canvas, revision, pixels).frameValue(),
        )

    fun revision(value: Long): Revision = Revision.create(value).frameValue()

    private fun position(
        x: Int,
        y: Int,
    ): PixelPosition =
        PixelPosition.create(
            PixelX.create(x).frameValue(),
            PixelY.create(y).frameValue(),
        )

    private fun color(
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ): PixelColor =
        PixelColor.create(
            ColorChannel.create(red).frameValue(),
            ColorChannel.create(green).frameValue(),
            ColorChannel.create(blue).frameValue(),
            ColorChannel.create(alpha).frameValue(),
        )

    private companion object {
        const val DOCUMENT_ID: String = "44444444444444444444444444444444"
        const val CHANNEL_MIN: Int = 0
        const val CHANNEL_MAX: Int = 255
    }
}

private fun <T> DomainValueResult<T>.frameValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> frameFailure("Frame fixture was rejected: $rejection")
    }

private fun frameFailure(message: String): Nothing = throw AssertionError(message)
