package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Rect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.workspace.ActualSizeScale
import io.github.hideyukimori.nenepixel.core.application.workspace.ActualSizeWindow
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorControlEdge
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorLayout
import io.github.hideyukimori.nenepixel.core.application.workspace.WindowAnchor
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The actual-size window is an exact integer multiple of the committed bitmap (ADR 0026).
 *
 * Pixel equality is checked against [toOpaqueRenderedBitmap], the one projection the canvas also
 * draws, rather than against a capture of `PixelCanvas`: the canvas fits the document to the work
 * area at a fractional scale and draws grid lines over it, so its own pixels are not a per-cell
 * reference. `CanvasBitmapProjectionTest` owns the snapshot-to-bitmap contract; this class owns the
 * bitmap-to-window contract.
 */
internal class ActualSizeWindowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyScaleDrawsTheCommittedDocumentAtItsExactIntegerMultiple() {
        val controller = controller()
        paint(controller, listOf(pixel(0, 0), pixel(1, 0)), index(1))
        paint(controller, listOf(pixel(3, 2)), index(2))
        setEditorContent(controller, WIDE_EDGE, TALL_EDGE)
        ActualSizeScale.entries.forEach { scale ->
            showWindow(controller, scale)
            val source = assertWindowMatchesCommittedBitmap(controller, scale)
            assertEquals("Scale $scale must show the whole document", DOCUMENT_WIDTH, source.columns)
            assertEquals("Scale $scale must show the whole document", DOCUMENT_HEIGHT, source.rows)
            assertTrue("The fixture must be discriminating", shownColors(controller, source).size > 1)
        }
    }

    @Test
    fun anInProgressStrokeLeavesTheWindowOnTheCommittedDocument() {
        val controller = controller()
        setEditorContent(controller, WIDE_EDGE, TALL_EDGE)
        composeRule.runOnIdle { controller.callbacks.onSelectPaletteEntry(index(1)) }
        showWindow(controller, ActualSizeScale.X8)
        val committed = windowPixels()
        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(center)
            moveBy(Offset(PREVIEW_STEP, 0f))
        }
        composeRule.waitForIdle()
        assertNotNull("The gesture must still be in progress", controller.renderState.preview)
        assertArrayEquals("An uncommitted gesture must not reach the window", committed, windowPixels())
        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput { up() }
        composeRule.waitForIdle()
        assertNull(controller.renderState.preview)
        assertFalse("The commit must reach the window", committed.contentEquals(windowPixels()))
        assertWindowMatchesCommittedBitmap(controller, ActualSizeScale.X8)
    }

    @Test
    fun aScaleAboveTheWindowLimitClipsTheDocumentCentreInsteadOfCoveringTheCanvas() {
        val controller = controller(width = LARGE_EDGE, height = LARGE_EDGE)
        paint(controller, List(LARGE_EDGE) { x -> pixel(x, 0) }, index(1))
        setEditorContent(controller, WIDE_EDGE, TALL_EDGE)
        showWindow(controller, ActualSizeScale.X8)
        val canvas = composeRule.onNodeWithTag("editor_canvas_${LARGE_EDGE}_$LARGE_EDGE").getUnclippedBoundsInRoot()
        val window = composeRule.onNodeWithTag(WINDOW_TAG).assertIsDisplayed().getUnclippedBoundsInRoot()
        val shorter = minOf(canvas.right - canvas.left, canvas.bottom - canvas.top)
        assertTrue("Window $window must not exceed half of $shorter", window.right - window.left <= shorter / 2f + 1.dp)
        assertTrue("Window $window must not exceed half of $shorter", window.bottom - window.top <= shorter / 2f + 1.dp)
        assertContained(canvas, window)
        val source = assertWindowMatchesCommittedBitmap(controller, ActualSizeScale.X8)
        assertTrue("The document must be clipped: ${source.columns} of $LARGE_EDGE", source.columns < LARGE_EDGE)
        assertEquals((LARGE_EDGE - source.columns) / 2, source.left)
        assertEquals((LARGE_EDGE - source.rows) / 2, source.top)
        assertTrue("A clipped window starts inside the document", source.left > 0 && source.top > 0)
        assertCentreCellIsShownFirst(controller, source)
        assertEquals(
            "The clipped centre excludes the painted first row",
            1,
            shownColors(controller, source).size,
        )
    }

    @Test
    fun theDockToggleShowsAndHidesTheWindowWithoutTouchingTheDocument() {
        val controller = controller()
        setEditorContent(controller, WIDE_EDGE, TALL_EDGE)
        val before = controller.renderState.snapshot
        composeRule.onNodeWithTag(WINDOW_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(TOGGLE_TAG).assertIsNotSelected().performClick()
        composeRule.onNodeWithTag(WINDOW_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(TOGGLE_TAG).assertIsSelected()
        assertTrue(controller.renderState.actualSizeWindow.visible)
        assertSame(before, controller.renderState.snapshot)
        composeRule.onNodeWithTag(TOGGLE_TAG).performClick()
        composeRule.onNodeWithTag(WINDOW_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(TOGGLE_TAG).assertIsNotSelected()
        assertSame(before, controller.renderState.snapshot)
    }

    @Test
    fun theWindowStaysInsideTheWorkAreaInBothLayoutsAndControlEdges() {
        val controller = controller()
        setEditorContent(controller, WIDE_EDGE, TALL_EDGE)
        showWindow(controller, ActualSizeScale.X8)
        val placements =
            listOf(
                EditorLayout.Tabletop to EditorControlEdge.Right,
                EditorLayout.Handheld to EditorControlEdge.Left,
                EditorLayout.Handheld to EditorControlEdge.Right,
            )
        placements.forEach { (layout, edge) ->
            composeRule.runOnIdle {
                controller.callbacks.onSetAppearance(
                    controller.renderState.appearance.copy(layout = layout, controlEdge = edge),
                )
            }
            ANCHORS.forEach { (x, y) -> assertWindowInsideCanvas(controller, x, y) }
        }
    }

    @Test
    fun draggingTheWindowPublishesOneAnchorThatMatchesWhereItLanded() {
        val controller = controller()
        setEditorContent(controller, WIDE_EDGE, TALL_EDGE)
        showWindow(controller, ActualSizeScale.X8)
        val published = CopyOnWriteArrayList<ActualSizeWindow>()
        val collector =
            CoroutineScope(Dispatchers.Unconfined).launch {
                controller.renderStates.collect { published += it.actualSizeWindow }
            }
        val before = boundsPixels(WINDOW_TAG)
        composeRule.onNodeWithTag(WINDOW_TAG).performTouchInput {
            down(center)
            moveBy(Offset(-DRAG_STEP, DRAG_STEP))
            moveBy(Offset(-DRAG_STEP, DRAG_STEP))
            up()
        }
        composeRule.waitForIdle()
        collector.cancel()

        assertEquals(
            "One release must publish exactly one window: $published",
            1,
            published.zipWithNext().count { (previous, next) -> previous != next },
        )
        val after = boundsPixels(WINDOW_TAG)
        assertTrue("The window must follow the drag: $before to $after", after.left < before.left)
        assertTrue("The window must follow the drag: $before to $after", after.top > before.top)
        assertPlacedAtAnchor(controller, after)
    }

    @Test
    fun tappingTheWindowCyclesTheScaleWithoutMovingIt() {
        val controller = controller()
        setEditorContent(controller, WIDE_EDGE, TALL_EDGE)
        showWindow(controller, ActualSizeScale.X2)
        setAnchor(controller, 0.0, 0.0)
        val canvas = boundsPixels(CANVAS_TAG)
        composeRule.onNodeWithTag(WINDOW_TAG).performClick()
        composeRule.waitForIdle()
        assertEquals(ActualSizeScale.X4, controller.renderState.actualSizeWindow.scale)
        assertEquals(WindowAnchor.create(0.0, 0.0), controller.renderState.actualSizeWindow.anchor)
        val window = boundsPixels(WINDOW_TAG)
        assertNear("An anchored window keeps its left edge", canvas.left, window.left)
        assertNear("An anchored window keeps its top edge", canvas.top, window.top)
    }

    @Test
    fun aTapThatJittersBelowTheTouchSlopLeavesNoResidualOffset() {
        val controller = controller()
        setEditorContent(controller, WIDE_EDGE, TALL_EDGE)
        showWindow(controller, ActualSizeScale.X4)
        setAnchor(controller, 0.0, 0.0)
        val canvas = boundsPixels(CANVAS_TAG)
        composeRule.onNodeWithTag(WINDOW_TAG).performTouchInput {
            down(center)
            moveBy(Offset(JITTER, JITTER))
            moveBy(Offset(-JITTER, JITTER))
            up()
        }
        composeRule.waitForIdle()
        assertEquals(ActualSizeScale.X8, controller.renderState.actualSizeWindow.scale)
        assertEquals(WindowAnchor.create(0.0, 0.0), controller.renderState.actualSizeWindow.anchor)
        val window = boundsPixels(WINDOW_TAG)
        assertNear("A jittering tap must not shift the window", canvas.left, window.left)
        assertNear("A jittering tap must not shift the window", canvas.top, window.top)
    }

    private fun assertCentreCellIsShownFirst(
        controller: EditorController,
        source: WindowSource,
    ) {
        val render = controller.renderState
        val expected = render.snapshot.toOpaqueRenderedBitmap(render.definition, canvasBackgroundArgb())
        val frame = framePixels()
        val image = composeRule.onNodeWithTag(WINDOW_TAG).captureToImage().toPixelMap()
        assertEquals(expected.getPixel(source.left, source.top), image[frame, frame].toArgb())
        assertFalse(
            "The clipped centre must differ from the painted first row",
            expected.getPixel(source.left, source.top) == expected.getPixel(source.left, 0),
        )
    }

    private fun assertPlacedAtAnchor(
        controller: EditorController,
        window: Rect,
    ) {
        val anchor = controller.renderState.actualSizeWindow.anchor
        val canvas = boundsPixels(CANVAS_TAG)
        val freeWidth = canvas.width() - window.width()
        val freeHeight = canvas.height() - window.height()
        assertNear("Anchor x must match the placement", canvas.left + (anchor.x * freeWidth).roundToInt(), window.left)
        assertNear("Anchor y must match the placement", canvas.top + (anchor.y * freeHeight).roundToInt(), window.top)
    }

    private fun assertWindowMatchesCommittedBitmap(
        controller: EditorController,
        scale: ActualSizeScale,
    ): WindowSource {
        val render = controller.renderState
        val expected = render.snapshot.toOpaqueRenderedBitmap(render.definition, canvasBackgroundArgb())
        val factor = scale.devicePixelsPerCell
        val frame = framePixels()
        val image = composeRule.onNodeWithTag(WINDOW_TAG).captureToImage().toPixelMap()
        val columns = (image.width - frame * 2) / factor
        val rows = (image.height - frame * 2) / factor
        assertEquals("Window width at $scale", columns * factor + frame * 2, image.width)
        assertEquals("Window height at $scale", rows * factor + frame * 2, image.height)
        val source = WindowSource((expected.width - columns) / 2, (expected.height - rows) / 2, columns, rows)
        repeat(rows) { y ->
            repeat(columns) { x ->
                val color = expected.getPixel(source.left + x, source.top + y)
                assertScaledCell(image, frame + x * factor, frame + y * factor, ScaledCell(factor, color))
            }
        }
        return source
    }

    /** The colours the committed bitmap holds inside [source]; the window must show exactly these. */
    private fun shownColors(
        controller: EditorController,
        source: WindowSource,
    ): Set<Int> {
        val render = controller.renderState
        val expected = render.snapshot.toOpaqueRenderedBitmap(render.definition, canvasBackgroundArgb())
        return buildSet {
            repeat(source.rows) { y ->
                repeat(source.columns) { x -> add(expected.getPixel(source.left + x, source.top + y)) }
            }
        }
    }

    private fun assertWindowInsideCanvas(
        controller: EditorController,
        x: Double,
        y: Double,
    ) {
        setAnchor(controller, x, y)
        val canvas = composeRule.onNodeWithTag(CANVAS_TAG).getUnclippedBoundsInRoot()
        val window = composeRule.onNodeWithTag(WINDOW_TAG).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertContained(canvas, window)
    }

    private fun assertScaledCell(
        image: PixelMap,
        left: Int,
        top: Int,
        cell: ScaledCell,
    ) {
        repeat(cell.scale) { dy ->
            repeat(cell.scale) { dx ->
                val actual = image[left + dx, top + dy].toArgb()
                if (actual != cell.color) {
                    assertEquals("Window pixel (${left + dx}, ${top + dy})", cell.color, actual)
                }
            }
        }
    }

    private fun assertContained(
        outer: DpRect,
        inner: DpRect,
    ) {
        assertTrue(
            "Bounds $inner outside $outer",
            inner.left >= outer.left && inner.top >= outer.top &&
                inner.right <= outer.right && inner.bottom <= outer.bottom,
        )
    }

    private fun assertNear(
        message: String,
        expected: Int,
        actual: Int,
    ) {
        assertTrue("$message: expected $expected, was $actual", abs(expected - actual) <= ROUNDING_PIXELS)
    }

    private fun windowPixels(): IntArray {
        val image = composeRule.onNodeWithTag(WINDOW_TAG).captureToImage().toPixelMap()
        return IntArray(image.width * image.height) { at ->
            image[at % image.width, at / image.width].toArgb()
        }
    }

    private fun boundsPixels(tag: String): Rect {
        val bounds = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        return with(composeRule.density) {
            Rect(bounds.left.roundToPx(), bounds.top.roundToPx(), bounds.right.roundToPx(), bounds.bottom.roundToPx())
        }
    }

    private fun framePixels(): Int = with(composeRule.density) { WINDOW_FRAME.roundToPx() }

    private fun canvasBackgroundArgb(): Int = PresentationPalette.canvasBackground.toArgb()

    private fun setAnchor(
        controller: EditorController,
        x: Double,
        y: Double,
    ) {
        composeRule.runOnIdle {
            controller.callbacks.onSetActualSizeWindow(
                controller.renderState.actualSizeWindow.withAnchor(WindowAnchor.create(x, y)),
            )
        }
    }

    private fun showWindow(
        controller: EditorController,
        scale: ActualSizeScale,
    ) {
        composeRule.runOnIdle {
            val current = controller.renderState.actualSizeWindow
            val shown = if (current.visible) current else current.toggled()
            controller.callbacks.onSetActualSizeWindow(shown.withScale(scale))
        }
    }

    private fun paint(
        controller: EditorController,
        path: List<PixelPosition>,
        index: PaletteIndex,
    ) {
        val stroke =
            Stroke.create(controller.renderState.snapshot.size, path, StrokeEffect.Paint(index)).requiredValue()
        controller.runtime.execute(ApplyStrokeCommand.create(controller.runtime.captureSource(), stroke))
        controller.synchronizeWithRuntime()
    }

    private fun setEditorContent(
        controller: EditorController,
        width: Dp,
        height: Dp,
    ) {
        composeRule.setContent {
            Box(Modifier.requiredSize(width, height).consumeWindowInsets(WindowInsets.safeDrawing).testTag(ROOT_TAG)) {
                TestNenePixelEditor(controller, Modifier.requiredSize(width, height))
            }
        }
    }

    private fun controller(
        width: Int = DOCUMENT_WIDTH,
        height: Int = DOCUMENT_HEIGHT,
    ): EditorController {
        val size =
            CanvasSize.create(
                CanvasWidth.create(width).requiredValue(),
                CanvasHeight.create(height).requiredValue(),
            )
        val palette =
            Palette
                .create(listOf(opaque(CHANNEL_MAX, 0, 0), opaque(0, CHANNEL_MAX, 0), opaque(0, 0, CHANNEL_MAX)))
                .requiredValue()
        val definition = PaletteDefinition.create(palette, index(0)).requiredValue()
        return EditorController.create(EditorRuntime.create(size, definition, FixedActualSizeDocumentIdSource))
    }

    private fun opaque(
        red: Int,
        green: Int,
        blue: Int,
    ): PixelColor =
        PixelColor.create(
            ColorChannel.create(red).requiredValue(),
            ColorChannel.create(green).requiredValue(),
            ColorChannel.create(blue).requiredValue(),
            ColorChannel.create(CHANNEL_MAX).requiredValue(),
        )

    private fun index(value: Int): PaletteIndex = PaletteIndex.create(value).requiredValue()

    private fun pixel(
        x: Int,
        y: Int,
    ): PixelPosition = PixelPosition.create(PixelX.create(x).requiredValue(), PixelY.create(y).requiredValue())

    private fun <T> DomainValueResult<T>.requiredValue(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> error("Invalid actual-size fixture: $rejection")
        }

    private data class ScaledCell(
        val scale: Int,
        val color: Int,
    )

    private data class WindowSource(
        val left: Int,
        val top: Int,
        val columns: Int,
        val rows: Int,
    )

    private companion object {
        val WINDOW_FRAME: Dp = 2.dp
        val WIDE_EDGE: Dp = 600.dp
        val TALL_EDGE: Dp = 400.dp
        val ANCHORS: List<Pair<Double, Double>> = listOf(0.0 to 0.0, 1.0 to 1.0, 0.5 to 0.5, 1.0 to 0.0)
        const val ROOT_TAG: String = "fixed actual size root"
        const val WINDOW_TAG: String = "editor_actual_size_window"
        const val TOGGLE_TAG: String = "editor_actual_size_window_toggle"
        const val CANVAS_TAG: String = "editor_canvas_4_3"
        const val DOCUMENT_WIDTH: Int = 4
        const val DOCUMENT_HEIGHT: Int = 3
        const val LARGE_EDGE: Int = 64
        const val CHANNEL_MAX: Int = 255
        const val ROUNDING_PIXELS: Int = 1
        const val DRAG_STEP: Float = 100f
        const val JITTER: Float = 2f
        const val PREVIEW_STEP: Float = 12f
    }
}

private object FixedActualSizeDocumentIdSource : DocumentIdSource {
    override fun nextDocumentId(): DocumentId =
        when (val result = DocumentId.create("2".repeat(DOCUMENT_ID_LENGTH))) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid actual-size fixture document ID: ${result.rejection}")
        }

    private const val DOCUMENT_ID_LENGTH: Int = 32
}
