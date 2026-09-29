package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.PointF
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.workspace.ActualSizeScale
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTransform
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Issue #147 B3: the canvas and the actual-size window show the transparency backdrop through
 * transparent and partially transparent pixels of the picture (ADR 0026). Each document pixel is
 * read at its centre, so grid lines never cover the point. The canvas backdrop starts at the corner
 * of the document rectangle; the window backdrop starts at the content corner.
 */
internal class TransparencyDisplayTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var currentEditor: EditorFixture

    @Test
    fun canvasShowsTheBackdropThroughEmptyPixelsAndBlendsTranslucentOnes() {
        val editor = paintedEditor()
        setEditorContent(editor)

        assertCanvasShows("committed", ::paintedSource)
    }

    @Test
    fun anEraserGestureShowsTheBackdropBeforeItEnds() {
        val editor = paintedEditor()
        setEditorContent(editor)
        composeRule.runOnIdle { editor.controller.callbacks.onSelectTool(DrawingTool.Eraser) }
        val transform = canvasTransform()
        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(centreOf(transform, pixel(0, 0)))
            moveTo(centreOf(transform, pixel(1, 0)))
        }
        composeRule.waitForIdle()
        assertNotNull("The gesture must still be in progress", editor.controller.renderState.preview)

        assertCanvasShows("erasing") { _, _ -> TRANSPARENT }
        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput { up() }
        composeRule.waitForIdle()
        assertNull(editor.controller.renderState.preview)
        assertCanvasShows("erased") { _, _ -> TRANSPARENT }
    }

    @Test
    fun actualSizeWindowShowsTheBackdropThroughEmptyPixelsAtEveryListedScale() {
        val editor = paintedEditor()
        setEditorContent(editor)

        WINDOW_SCALES.forEach { scale ->
            composeRule.runOnIdle {
                val current = editor.controller.renderState.actualSizeWindow
                val shown = if (current.visible) current else current.toggled()
                editor.controller.callbacks.onSetActualSizeWindow(shown.withScale(scale))
            }
            assertWindowShows(scale)
        }
    }

    @Test
    fun aRunningStrokeKeepsTheCommittedBitmapReference() {
        val editor = paintedEditor()
        val adapter = EditorRuntimeAdapter(editor.runtime)
        val committed = CommittedBitmapCache()
        val previews = PreviewBitmapCache()
        val before = committed.render(adapter.renderState.document, adapter.renderState.definition, null)

        adapter.reduce(WorkspaceAction.BeginGesturePreview(adapter.renderState.document.size, pixel(0, 0)))
        val first = requireNotNull(previews.render(adapter.renderState, committed))
        adapter.reduce(WorkspaceAction.ExtendGesturePreview(pixel(WIDTH - 1, HEIGHT - 1)))
        val second = requireNotNull(previews.render(adapter.renderState, committed))

        val state = adapter.renderState
        val after = committed.render(state.document, state.definition, null)
        assertSame("The stroke must not rebuild the committed bitmap", before, after)
        assertSame("The working bitmap is reused between positions", first, second)
        assertTrue("The committed bitmap keeps alpha", before.hasAlpha())
        assertTrue("The working bitmap keeps alpha", second.hasAlpha())
    }

    private fun assertCanvasShows(
        label: String,
        sourceAt: (x: Int, y: Int) -> Int,
    ) {
        val image = composeRule.onNodeWithTag(CANVAS_TAG).captureToImage().toPixelMap()
        val transform = transformFor(image.width, image.height)
        val corner = checkNotNull(transform.surfaceBounds(pixel(0, 0)))
        val origin = PointF(corner.left.toFloat(), corner.top.toFloat())
        val cellPx = TransparencyBackdrop.cellPx(composeRule.density.density)
        forEachDocumentPixel { x, y ->
            val centre = centreOf(transform, pixel(x, y))
            val px = centre.x.toInt()
            val py = centre.y.toInt()
            val source = sourceAt(x, y)
            val backdrop = TransparencyExpectation.backdropAt(px, py, origin, cellPx)
            TransparencyExpectation.assertArgbNear(
                "$label canvas pixel ($x, $y) at ($px, $py)",
                TransparencyExpectation.shown(source, backdrop),
                image[px, py].toArgb(),
                TransparencyExpectation.toleranceFor(source),
            )
        }
    }

    private fun assertWindowShows(scale: ActualSizeScale) {
        val image = composeRule.onNodeWithTag(CONTENT_TAG).captureToImage().toPixelMap()
        val factor = scale.devicePixelsPerCell
        assertEquals("Scale $scale must show every column", WIDTH, image.width / factor)
        assertEquals("Scale $scale must show every row", HEIGHT, image.height / factor)
        val cellPx = TransparencyBackdrop.cellPx(composeRule.density.density)
        forEachDocumentPixel { x, y ->
            val px = x * factor + factor / 2
            val py = y * factor + factor / 2
            val source = paintedSource(x, y)
            TransparencyExpectation.assertArgbNear(
                "$scale window pixel ($x, $y) at ($px, $py)",
                TransparencyExpectation.shown(source, TransparencyBackdrop.colorAt(px, py, cellPx)),
                image[px, py].toArgb(),
                TransparencyExpectation.toleranceFor(source),
            )
        }
    }

    private fun canvasTransform(): ViewportTransform {
        val image = composeRule.onNodeWithTag(CANVAS_TAG).captureToImage()
        return transformFor(image.width, image.height)
    }

    private fun transformFor(
        width: Int,
        height: Int,
    ): ViewportTransform {
        val surface =
            when (val result = ViewportSurface.create(width, height, composeRule.density.density.toDouble())) {
                is ViewportValueResult.Created -> result.value
                is ViewportValueResult.Rejected -> error("Invalid test surface: ${result.rejection}")
            }
        val state = currentEditor.controller.renderState
        return checkNotNull(createViewportTransform(state.document.size, surface, state.viewport))
    }

    private fun setEditorContent(editor: EditorFixture) {
        currentEditor = editor
        composeRule.setContent {
            Box(Modifier.requiredSize(WIDE_EDGE, TALL_EDGE).consumeWindowInsets(WindowInsets.safeDrawing)) {
                TestNenePixelEditor(editor.controller, Modifier.requiredSize(WIDE_EDGE, TALL_EDGE))
            }
        }
        composeRule.waitForIdle()
    }

    /** Opaque red at (0, 0), half-transparent blue at (1, 0); every other pixel is Empty. */
    private fun paintedEditor(): EditorFixture {
        val editor = fixture(canvas(WIDTH, HEIGHT), listOf(PresentationTestValues.red, halfBlue))
        paint(editor, pixel(0, 0), 0)
        paint(editor, pixel(1, 0), 1)
        return editor
    }

    private fun paint(
        editor: EditorFixture,
        position: PixelPosition,
        index: Int,
    ) {
        val runtime = editor.runtime
        val effect = StrokeEffect.Paint(PaletteIndex.create(index).requiredValue())
        val stroke = Stroke.create(runtime.state.documentState.size, listOf(position), effect).requiredValue()
        runtime.execute(ApplyStrokeCommand.create(runtime.captureSource(), LayerId.first(), stroke))
        editor.controller.synchronizeWithRuntime()
    }

    private companion object {
        const val WIDTH: Int = 4
        const val HEIGHT: Int = 3
        const val CANVAS_TAG: String = "editor_canvas_4_3"
        const val CONTENT_TAG: String = "editor_actual_size_window_content"
        const val OPAQUE_RED: Int = 0xFFFF0000.toInt()
        const val HALF_BLUE: Int = 0x800000FF.toInt()
        const val TRANSPARENT: Int = 0
        val WIDE_EDGE: Dp = 600.dp
        val TALL_EDGE: Dp = 400.dp
        val WINDOW_SCALES: List<ActualSizeScale> =
            listOf(ActualSizeScale.X1, ActualSizeScale.X2, ActualSizeScale.X4, ActualSizeScale.X8)
        val halfBlue: PixelColor = PixelColor.fromPackedRgba8888(0x0000FF80)

        /** Straight-alpha ARGB the painted fixture holds at ([x], [y]). */
        fun paintedSource(
            x: Int,
            y: Int,
        ): Int =
            when {
                y != 0 -> TRANSPARENT
                x == 0 -> OPAQUE_RED
                x == 1 -> HALF_BLUE
                else -> TRANSPARENT
            }

        fun forEachDocumentPixel(action: (x: Int, y: Int) -> Unit) {
            repeat(HEIGHT) { y -> repeat(WIDTH) { x -> action(x, y) } }
        }

        fun centreOf(
            transform: ViewportTransform,
            position: PixelPosition,
        ): Offset {
            val bounds = checkNotNull(transform.surfaceBounds(position))
            val x = (bounds.left + bounds.right) / 2.0
            val y = (bounds.top + bounds.bottom) / 2.0
            return Offset(x.toFloat(), y.toFloat())
        }

        fun pixel(
            x: Int,
            y: Int,
        ): PixelPosition = PixelPosition.create(PixelX.create(x).requiredValue(), PixelY.create(y).requiredValue())
    }
}

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Invalid transparency display fixture: $rejection")
    }
