package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.PointF
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTransform
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.EditorFixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.canvas
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture

/**
 * Shared set-up for [UnderlayDisplayTest] (Issue #170 A6): a 4 x 3 document with opaque red at
 * (0, 0) and every other pixel Empty, opaque green underlay images, and canvas read-back at the
 * centre of each document pixel so grid lines never cover the point.
 */
internal object UnderlayDisplayFixture {
    const val WIDTH: Int = 4
    const val HEIGHT: Int = 3
    const val CANVAS_TAG: String = "editor_canvas_4_3"
    const val CONTENT_TAG: String = "editor_actual_size_window_content"
    const val OPAQUE_RED: Int = 0xFFFF0000.toInt()
    const val OPAQUE_GREEN: Int = 0xFF00FF00.toInt()
    private const val GREEN_RGBA: Int = 0x00FF00FF
    private val WIDE_EDGE: Dp = 600.dp
    private val TALL_EDGE: Dp = 400.dp

    fun paintedEditor(): EditorFixture {
        val editor = fixture(canvas(WIDTH, HEIGHT), listOf(PresentationTestValues.red))
        val runtime = editor.runtime
        val effect = StrokeEffect.Paint(PaletteIndex.create(0).requiredValue())
        val stroke = Stroke.create(runtime.state.documentState.size, listOf(pixel(0, 0)), effect).requiredValue()
        runtime.execute(ApplyStrokeCommand.create(runtime.captureSource(), LayerId.first(), stroke))
        editor.controller.synchronizeWithRuntime()
        return editor
    }

    /** An opaque green image of [width] x [height] image pixels. */
    fun greenImage(
        width: Int,
        height: Int,
    ): ReferenceImage =
        when (val result = ReferenceImage.create(width, height, IntArray(width * height) { GREEN_RGBA })) {
            is ReferenceImageResult.Created -> result.image
            is ReferenceImageResult.Rejected -> error("Invalid underlay fixture: ${result.reason}")
        }

    fun setContent(
        rule: ComposeContentTestRule,
        editor: EditorFixture,
    ) {
        rule.setContent {
            Box(Modifier.requiredSize(WIDE_EDGE, TALL_EDGE).consumeWindowInsets(WindowInsets.safeDrawing)) {
                TestNenePixelEditor(editor.controller, Modifier.requiredSize(WIDE_EDGE, TALL_EDGE))
            }
        }
        rule.waitForIdle()
    }

    /** Sets the underlay [build] makes from the document size, through the underlay route. */
    fun setUnderlay(
        rule: ComposeContentTestRule,
        editor: EditorFixture,
        build: (CanvasSize) -> ReferenceUnderlay,
    ) {
        rule.runOnIdle {
            val callbacks = editor.controller.callbacks.underlay
            callbacks.onSet(build(editor.controller.renderState.document.size))
        }
        rule.waitForIdle()
    }

    fun readCanvas(
        rule: ComposeContentTestRule,
        editor: EditorFixture,
    ): Reading {
        val pixels = rule.onNodeWithTag(CANVAS_TAG).captureToImage().toPixelMap()
        val surface =
            when (val result = ViewportSurface.create(pixels.width, pixels.height, rule.density.density.toDouble())) {
                is ViewportValueResult.Created -> result.value
                is ViewportValueResult.Rejected -> error("Invalid test surface: ${result.rejection}")
            }
        val state = editor.controller.renderState
        val transform = checkNotNull(createViewportTransform(state.document.size, surface, state.viewport))
        return Reading(pixels, transform, TransparencyBackdrop.cellPx(rule.density.density))
    }

    fun pixel(
        x: Int,
        y: Int,
    ): PixelPosition = PixelPosition.create(PixelX.create(x).requiredValue(), PixelY.create(y).requiredValue())

    fun forEachDocumentPixel(action: (x: Int, y: Int) -> Unit) {
        repeat(HEIGHT) { y -> repeat(WIDTH) { x -> action(x, y) } }
    }

    private fun <T> DomainValueResult<T>.requiredValue(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> error("Invalid underlay display fixture: $rejection")
        }

    /** One capture of the canvas, read in device pixels of the capture. */
    class Reading(
        private val pixels: PixelMap,
        private val transform: ViewportTransform,
        private val cellPx: Int,
    ) {
        private val corner = checkNotNull(transform.surfaceBounds(pixel(0, 0)))
        private val farCorner = checkNotNull(transform.surfaceBounds(pixel(WIDTH - 1, HEIGHT - 1)))
        private val origin = PointF(corner.left.toFloat(), corner.top.toFloat())

        val width: Int get() = pixels.width
        val height: Int get() = pixels.height

        /** First device column right of the document rectangle. */
        val beyondRight: Int get() = farCorner.right.toInt() + OUTSIDE_MARGIN

        /** First device row below the document rectangle. */
        val beyondBottom: Int get() = farCorner.bottom.toInt() + OUTSIDE_MARGIN

        fun centreX(x: Int): Int = centreOf(x, 0).first

        fun centreY(y: Int): Int = centreOf(0, y).second

        /** ARGB shown at the centre of document pixel ([x], [y]). */
        fun shownAt(
            x: Int,
            y: Int,
        ): Int = at(centreX(x), centreY(y))

        /** Backdrop colour under the centre of document pixel ([x], [y]). */
        fun backdropAt(
            x: Int,
            y: Int,
        ): Int = TransparencyExpectation.backdropAt(centreX(x), centreY(y), origin, cellPx)

        fun at(
            px: Int,
            py: Int,
        ): Int = pixels[px, py].toArgb()

        private fun centreOf(
            x: Int,
            y: Int,
        ): Pair<Int, Int> {
            val bounds = checkNotNull(transform.surfaceBounds(pixel(x, y)))
            return ((bounds.left + bounds.right) / 2.0).toInt() to ((bounds.top + bounds.bottom) / 2.0).toInt()
        }

        private companion object {
            /** Device pixels past the document edge, clear of the edge pixel the clip may round. */
            const val OUTSIDE_MARGIN: Int = 2
        }
    }
}
