package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/**
 * Landscape 600x400dp leaves the canvas only about 22dp of height, so it is height constrained and
 * renders at a few dozen device pixels. The document ratio is therefore asserted in whole device
 * pixels rather than in dp, because one pixel of rounding is over one percent of such a small canvas.
 */
internal class EditorScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun landscapeKeepsWideCanvasInsideAllocatedAreaAndControlsReachable() {
        setEditorContent(width = LANDSCAPE_WIDTH, height = LANDSCAPE_HEIGHT)

        val root = composeRule.onNodeWithTag(ROOT_TAG).getUnclippedBoundsInRoot()
        val canvas = canvasBounds()
        val controlBounds = controlBounds()
        val diagnostics = layoutDiagnostics(root, canvas, controlBounds)

        assertNonEmpty(canvas, diagnostics)
        assertContained(root, canvas, "canvas", diagnostics)
        controlBounds.forEach { (name, bounds) -> assertContained(root, bounds, name, diagnostics) }
        assertTrue(
            "canvas must start below every editor control: $diagnostics",
            controlBounds.all { (_, bounds) -> canvas.top >= bounds.bottom },
        )
        assertAspectRatio(canvas, diagnostics)
    }

    @Test
    fun portraitKeepsCurrentFullWidthThreeByTwoGeometryAndControlsReachable() {
        setEditorContent(width = PORTRAIT_WIDTH, height = PORTRAIT_HEIGHT)

        val root = composeRule.onNodeWithTag(ROOT_TAG).getUnclippedBoundsInRoot()
        val canvas = canvasBounds()
        val controlBounds = controlBounds()

        val diagnostics = layoutDiagnostics(root, canvas, controlBounds)

        assertNonEmpty(canvas, diagnostics)
        assertContained(root, canvas, "canvas", diagnostics)
        controlBounds.forEach { (name, bounds) -> assertContained(root, bounds, name, diagnostics) }
        assertTrue(
            "canvas must start below every editor control: $diagnostics",
            controlBounds.all { (_, bounds) -> canvas.top >= bounds.bottom },
        )
        assertEquals(diagnostics, PORTRAIT_CANVAS_WIDTH.value, canvas.widthValue(), DP_TOLERANCE)
        assertEquals(diagnostics, PORTRAIT_CANVAS_HEIGHT.value, canvas.heightValue(), DP_TOLERANCE)
        assertAspectRatio(canvas, diagnostics)
    }

    private fun setEditorContent(
        width: Dp,
        height: Dp,
    ) {
        val controller = controller()
        composeRule.setContent {
            Box(modifier = Modifier.requiredSize(width, height).testTag(ROOT_TAG)) {
                TestNenePixelEditor(controller, Modifier.requiredSize(width, height))
            }
        }
    }

    private fun canvasBounds(): DpRect =
        composeRule
            .onNodeWithContentDescription(CANVAS_DESCRIPTION)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()

    private fun controlBounds(): List<Pair<String, DpRect>> =
        listOf(
            "Pencil" to
                composeRule
                    .onNodeWithContentDescription("Pencil tool")
                    .assertIsDisplayed()
                    .getUnclippedBoundsInRoot(),
            "Eraser" to
                composeRule
                    .onNodeWithContentDescription("Eraser tool")
                    .assertIsDisplayed()
                    .getUnclippedBoundsInRoot(),
            "Palette" to
                composeRule
                    .onNodeWithContentDescription(FIRST_PALETTE_DESCRIPTION)
                    .assertIsDisplayed()
                    .getUnclippedBoundsInRoot(),
            "New document" to
                composeRule
                    .onNodeWithText("New document")
                    .assertIsDisplayed()
                    .getUnclippedBoundsInRoot(),
            "Dirty status" to
                composeRule
                    .onNodeWithText("No unsaved changes")
                    .assertIsDisplayed()
                    .getUnclippedBoundsInRoot(),
            "Undo" to
                composeRule
                    .onNodeWithText("Undo")
                    .assertIsDisplayed()
                    .getUnclippedBoundsInRoot(),
            "Redo" to
                composeRule
                    .onNodeWithText("Redo")
                    .assertIsDisplayed()
                    .getUnclippedBoundsInRoot(),
        )

    private fun layoutDiagnostics(
        root: DpRect,
        canvas: DpRect,
        controls: List<Pair<String, DpRect>>,
    ): String {
        val clipped =
            composeRule
                .onNodeWithContentDescription(CANVAS_DESCRIPTION)
                .getBoundsInRoot()
        val lines =
            listOf(
                "$LAYOUT_MARKER root=${root.describe()}",
                "$LAYOUT_MARKER canvasUnclipped=${canvas.describe()}",
                "$LAYOUT_MARKER canvasClipped=${clipped.describe()}",
            ) + controls.map { (name, bounds) -> "$LAYOUT_MARKER control[$name]=${bounds.describe()}" }
        return lines.joinToString(separator = System.lineSeparator())
    }

    private fun DpRect.describe(): String =
        "left=${left.value} top=${top.value} right=${right.value} bottom=${bottom.value} " +
            "w=${widthValue()} h=${heightValue()} ratio=${widthValue() / heightValue()}"

    private fun assertContained(
        outer: DpRect,
        inner: DpRect,
        name: String,
        diagnostics: String,
    ) {
        assertTrue("$name left edge is outside root: $diagnostics", inner.left >= outer.left)
        assertTrue("$name top edge is outside root: $diagnostics", inner.top >= outer.top)
        assertTrue("$name right edge is outside root: $diagnostics", inner.right <= outer.right)
        assertTrue("$name bottom edge is outside root: $diagnostics", inner.bottom <= outer.bottom)
    }

    private fun assertNonEmpty(
        bounds: DpRect,
        diagnostics: String,
    ) {
        assertTrue("canvas width must be positive: $diagnostics", bounds.widthValue() > 0f)
        assertTrue("canvas height must be positive: $diagnostics", bounds.heightValue() > 0f)
    }

    private fun assertAspectRatio(
        bounds: DpRect,
        diagnostics: String,
    ) {
        val widthPx = with(composeRule.density) { (bounds.right - bounds.left).toPx() }
        val heightPx = with(composeRule.density) { (bounds.bottom - bounds.top).toPx() }
        val expectedWidthPx = heightPx * DOCUMENT_ASPECT_RATIO
        assertTrue(
            "canvas must keep the document ratio within one device pixel: " +
                "widthPx=$widthPx heightPx=$heightPx expectedWidthPx=$expectedWidthPx $diagnostics",
            abs(widthPx - expectedWidthPx) <= PIXEL_TOLERANCE,
        )
    }

    private fun DpRect.widthValue(): Float = right.value - left.value

    private fun DpRect.heightValue(): Float = bottom.value - top.value

    private fun controller(): EditorController {
        val size =
            CanvasSize.create(
                CanvasWidth.create(DOCUMENT_WIDTH).requiredValue(),
                CanvasHeight.create(DOCUMENT_HEIGHT).requiredValue(),
            )
        val palette =
            Palette
                .create(
                    listOf(
                        PixelColor.create(
                            ColorChannel.create(CHANNEL_MAX).requiredValue(),
                            ColorChannel.create(CHANNEL_MIN).requiredValue(),
                            ColorChannel.create(CHANNEL_MIN).requiredValue(),
                            ColorChannel.create(CHANNEL_MAX).requiredValue(),
                        ),
                    ),
                ).requiredValue()
        return EditorController.create(EditorRuntime.create(size, palette, FixedDocumentIdSource))
    }

    private fun <T> DomainValueResult<T>.requiredValue(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> error("Invalid layout fixture: $rejection")
        }

    private companion object {
        const val ROOT_TAG: String = "fixed editor root"
        const val DOCUMENT_WIDTH: Int = 3
        const val DOCUMENT_HEIGHT: Int = 2
        const val CHANNEL_MIN: Int = 0
        const val CHANNEL_MAX: Int = 255
        const val CANVAS_DESCRIPTION: String = "3 by 2 pixel canvas"
        const val LAYOUT_MARKER: String = "NENE_LAYOUT"
        const val DOCUMENT_ASPECT_RATIO: Float = 1.5f
        const val DP_TOLERANCE: Float = 1f
        const val PIXEL_TOLERANCE: Float = 1.0f
        const val FIRST_PALETTE_DESCRIPTION: String = "Palette color 1, RGBA 255, 0, 0, 255"
        val LANDSCAPE_WIDTH: Dp = 600.dp
        val LANDSCAPE_HEIGHT: Dp = 400.dp
        val PORTRAIT_WIDTH: Dp = 600.dp
        val PORTRAIT_HEIGHT: Dp = 900.dp
        val PORTRAIT_CANVAS_WIDTH: Dp = 552.dp
        val PORTRAIT_CANVAS_HEIGHT: Dp = 368.dp
    }
}

private object FixedDocumentIdSource : DocumentIdSource {
    override fun nextDocumentId(): DocumentId =
        when (val result = DocumentId.create("1".repeat(DOCUMENT_ID_LENGTH))) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid layout fixture document ID: ${result.rejection}")
        }

    private const val DOCUMENT_ID_LENGTH: Int = 32
}
