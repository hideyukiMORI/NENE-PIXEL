package io.github.hideyukimori.nenepixel

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
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.editor.FixedSliceEditorController

internal fun createFixedSliceEditorController(): FixedSliceEditorController {
    val canvas =
        CanvasSize.create(
            CanvasWidth.create(FIXED_CANVAS_EDGE).requiredValue(),
            CanvasHeight.create(FIXED_CANVAS_EDGE).requiredValue(),
        )
    val background = color(red = CHANNEL_MAX, green = CHANNEL_MAX, blue = CHANNEL_MAX)
    val activeColor = color(red = CHANNEL_MAX, green = CHANNEL_MIN, blue = CHANNEL_MIN)
    val snapshot =
        PixelSnapshot
            .create(
                size = canvas,
                revision = Revision.initial(),
                pixels = List(canvas.pixelCount.toInt()) { background },
            ).requiredValue()
    val document =
        DocumentState.create(
            id = DocumentId.create(FIXED_DOCUMENT_ID).requiredValue(),
            snapshot = snapshot,
        )
    return FixedSliceEditorController.create(
        commandGateway = CommandGateway.create(document),
        workspaceReducer = WorkspaceReducer.create(),
        initialWorkspaceState = WorkspaceState.create(activeColor),
    )
}

private fun color(
    red: Int,
    green: Int,
    blue: Int,
): PixelColor =
    PixelColor.create(
        red = ColorChannel.create(red).requiredValue(),
        green = ColorChannel.create(green).requiredValue(),
        blue = ColorChannel.create(blue).requiredValue(),
        alpha = ColorChannel.create(CHANNEL_MAX).requiredValue(),
    )

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Fixed application composition is invalid: $rejection")
    }

private const val FIXED_CANVAS_EDGE: Int = 16
private const val FIXED_DOCUMENT_ID: String = "00000000000000000000000000000000"
private const val CHANNEL_MIN: Int = 0
private const val CHANNEL_MAX: Int = 255
