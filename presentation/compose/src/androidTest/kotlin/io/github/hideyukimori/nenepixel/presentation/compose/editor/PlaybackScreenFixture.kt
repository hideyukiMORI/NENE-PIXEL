package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameDuration
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

internal object PlaybackScreenFixture {
    fun controller(): EditorController {
        val controller = FrameScreenFixture.twoPaintedFrames().controller
        val frames = controller.documentState.frames
        controller.callbacks.frames.onSelect(frames.first().id)
        paint(controller, 0)
        controller.callbacks.frames.onSelect(frames.last().id)
        paint(controller, 1)
        controller.callbacks.frames.onDuration(frames.first().id, FrameDuration.create(10).frameValue())
        controller.callbacks.frames.onDuration(frames.last().id, FrameDuration.create(60_000).frameValue())
        controller.callbacks.frames.onSelect(frames.first().id)
        return controller
    }

    fun offset(
        scope: TouchInjectionScope,
        rule: ComposeContentTestRule,
        controller: EditorController,
        pixel: PixelPosition,
    ): Offset = centre(IntSize(scope.width, scope.height), rule, controller, pixel)

    fun canvasPixel(
        rule: ComposeContentTestRule,
        controller: EditorController,
    ): Int {
        val image = rule.onNodeWithTag(CANVAS_TAG).captureToImage().toPixelMap()
        val centre = centre(IntSize(image.width, image.height), rule, controller, FrameTestValues.position(3, 3))
        return image[centre.x.toInt(), centre.y.toInt()].toArgb()
    }

    fun windowPixel(rule: ComposeContentTestRule): Int {
        val image = rule.onNodeWithTag("editor_actual_size_window_content").captureToImage().toPixelMap()
        return image[CELL_CENTRE, CELL_CENTRE].toArgb()
    }

    fun back() = InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

    private fun centre(
        size: IntSize,
        rule: ComposeContentTestRule,
        controller: EditorController,
        pixel: PixelPosition,
    ): Offset {
        val result = ViewportSurface.create(size.width, size.height, rule.density.density.toDouble())
        val surface =
            when (result) {
                is ViewportValueResult.Created -> result.value
                is ViewportValueResult.Rejected -> error("Invalid playback surface: ${result.rejection}")
            }
        val state = controller.renderState
        val transform = requireNotNull(createViewportTransform(state.document.size, surface, state.viewport))
        val bounds = requireNotNull(transform.surfaceBounds(pixel))
        return Offset(
            ((bounds.left + bounds.right) / DIVISOR).toFloat(),
            ((bounds.top + bounds.bottom) / DIVISOR).toFloat(),
        )
    }

    private fun paint(
        controller: EditorController,
        index: Int,
    ) {
        val state = controller.runtime.state
        val stroke =
            Stroke
                .create(
                    state.documentState.size,
                    listOf(FrameTestValues.position(3, 3)),
                    StrokeEffect.Paint(PaletteIndex.create(index).frameValue()),
                ).frameValue()
        controller.runtime.execute(
            ApplyStrokeCommand.create(
                controller.runtime.captureSource(),
                state.workspaceState.activeLayerId,
                stroke,
            ),
        )
        controller.synchronizeWithRuntime()
    }

    const val CANVAS_TAG = "editor_canvas_4_4"
    private const val CELL_CENTRE = 16
    private const val DIVISOR = 2.0
}
