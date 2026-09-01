package io.github.hideyukimori.nenepixel.presentation.compose

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReducer
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceState
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.editor.FixedSliceEditorController
import org.junit.jupiter.api.fail

internal object PresentationTestValues {
    val white: PixelColor = color(255, 255, 255)
    val red: PixelColor = color(255, 0, 0)

    fun canvas(
        width: Int,
        height: Int,
    ): CanvasSize =
        CanvasSize.create(
            CanvasWidth.create(width).requiredValue(),
            CanvasHeight.create(height).requiredValue(),
        )

    fun position(
        x: Int,
        y: Int,
    ): PixelPosition = PixelPosition.create(PixelX.create(x).requiredValue(), PixelY.create(y).requiredValue())

    fun fixture(
        canvas: CanvasSize = canvas(4, 4),
        activeColor: PixelColor = red,
    ): EditorFixture {
        val snapshot =
            PixelSnapshot
                .create(
                    canvas,
                    Revision.initial(),
                    List(canvas.pixelCount.toInt()) { white },
                ).requiredValue()
        val document = DocumentState.create(DocumentId.create(DOCUMENT_ID).requiredValue(), snapshot)
        val gateway = CommandGateway.create(document)
        val reducer = WorkspaceReducer.create()
        val initialWorkspace = WorkspaceState.create(activeColor)
        return EditorFixture(
            initialDocument = document,
            gateway = gateway,
            reducer = reducer,
            initialWorkspace = initialWorkspace,
            controller = FixedSliceEditorController.create(gateway, reducer, initialWorkspace),
        )
    }

    fun colorAt(
        state: DocumentState,
        position: PixelPosition,
    ): PixelColor = state.snapshot.colorAt(position).requiredValue()

    private fun color(
        red: Int,
        green: Int,
        blue: Int,
    ): PixelColor =
        PixelColor.create(
            ColorChannel.create(red).requiredValue(),
            ColorChannel.create(green).requiredValue(),
            ColorChannel.create(blue).requiredValue(),
            ColorChannel.create(255).requiredValue(),
        )

    private const val DOCUMENT_ID: String = "11111111111111111111111111111111"
}

internal data class EditorFixture(
    val initialDocument: DocumentState,
    val gateway: CommandGateway,
    val reducer: WorkspaceReducer,
    val initialWorkspace: WorkspaceState,
    val controller: FixedSliceEditorController,
)

internal fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> fail("Test value was rejected: $rejection")
    }
