package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.hideyukimori.nenepixel.core.application.workspace.ActualSizeScale
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayOpacity
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.presentation.compose.EditorFixture
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayDisplayFixture.CONTENT_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayDisplayFixture.HEIGHT
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayDisplayFixture.OPAQUE_BLUE
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayDisplayFixture.OPAQUE_GREEN
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayDisplayFixture.OPAQUE_RED
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayDisplayFixture.WIDTH
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayDisplayFixture.forEachDocumentPixel
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayDisplayFixture.greenBlueColumnsImage
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayDisplayFixture.greenImage
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayDisplayFixture.paintedEditor
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayDisplayFixture.readCanvas
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayDisplayFixture.setContent
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayDisplayFixture.setUnderlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Issue #170 A6: the canvas draws the reference underlay between the transparency backdrop and the
 * picture, clipped to the document rectangle, and the actual-size window never shows it (ADR 0032).
 * The 4 x 3 fixture holds opaque red at (0, 0); every other pixel is Empty. Tolerances per channel:
 * 0 for an opaque underlay, which is sampled nearest-neighbour (Issue #175), [BLEND_TOLERANCE] for
 * the half-opacity blend (paint alpha and premultiplication each round), 0 where no underlay is
 * drawn.
 */
internal class UnderlayDisplayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fullOpacityShowsTheUnderlayThroughEmptyPixels() {
        val editor = shownEditor { size -> opaque(greenImage(WIDTH, HEIGHT), size) }
        val reading = readCanvas(composeRule, editor)

        forEachEmptyPixel { x, y ->
            TransparencyExpectation.assertArgbNear("($x, $y)", OPAQUE_GREEN, reading.shownAt(x, y), 0)
        }
    }

    @Test
    fun anEnlargedUnderlayShowsItsPixelsWithoutInterpolation() {
        // At scale 2 each image pixel covers 2 x 2 document pixels: columns 0-1 green, 2-3 blue.
        val editor = shownEditor { size -> opaque(greenBlueColumnsImage(), size).withPlacement(0.0, 0.0, 2.0) }
        val reading = readCanvas(composeRule, editor)

        forEachEmptyPixel { x, y ->
            val expected = if (x < 2) OPAQUE_GREEN else OPAQUE_BLUE
            TransparencyExpectation.assertArgbNear("($x, $y)", expected, reading.shownAt(x, y), 0)
        }
    }

    @Test
    fun paintedPixelsHideTheUnderlay() {
        val editor = shownEditor { size -> opaque(greenImage(WIDTH, HEIGHT), size) }

        val shown = readCanvas(composeRule, editor).shownAt(0, 0)

        TransparencyExpectation.assertArgbNear("painted (0, 0)", OPAQUE_RED, shown, 0)
    }

    @Test
    fun hidingOrClearingTheUnderlayShowsTheBackdropAgain() {
        val editor = shownEditor { size -> opaque(greenImage(WIDTH, HEIGHT), size) }
        val callbacks = editor.controller.callbacks.underlay

        composeRule.runOnIdle {
            val shown = checkNotNull(editor.controller.renderState.underlay)
            callbacks.onSet(shown.toggledVisibility())
        }
        composeRule.waitForIdle()
        assertBackdropInEmptyPixels("hidden", editor)

        setUnderlay(composeRule, editor) { size -> opaque(greenImage(WIDTH, HEIGHT), size) }
        composeRule.runOnIdle { callbacks.onClear() }
        composeRule.waitForIdle()
        assertBackdropInEmptyPixels("cleared", editor)
    }

    @Test
    fun halfOpacityBlendsTheUnderlayWithTheLightSquares() {
        val editor =
            shownEditor { size ->
                ReferenceUnderlay.placed(greenImage(WIDTH, HEIGHT), size).withOpacity(UnderlayOpacity.create(HALF))
            }
        val reading = readCanvas(composeRule, editor)
        val light = PresentationPalette.transparencyLight.toArgb()
        var overLight = 0

        forEachEmptyPixel { x, y ->
            val backdrop = reading.backdropAt(x, y)
            if (backdrop == light) overLight++
            val expected = TransparencyExpectation.shown(HALF_GREEN, backdrop)
            TransparencyExpectation.assertArgbNear("($x, $y)", expected, reading.shownAt(x, y), BLEND_TOLERANCE)
        }
        assertTrue("At least one empty pixel must lie over a light square", overLight > 0)
    }

    @Test
    fun aFittedImageOfAnotherAspectLeavesTheMarginsTransparent() {
        // A 4 x 1 image fitted to 4 x 3 covers row 1 only; rows 0 and 2 are margin.
        val editor = shownEditor { size -> opaque(greenImage(WIDTH, 1), size) }
        val reading = readCanvas(composeRule, editor)

        forEachEmptyPixel { x, y ->
            val expected = if (y == 1) OPAQUE_GREEN else reading.backdropAt(x, y)
            TransparencyExpectation.assertArgbNear("($x, $y)", expected, reading.shownAt(x, y), 0)
        }
    }

    @Test
    fun anImageBeyondTheDocumentLeavesTheSurroundUntouched() {
        val editor = paintedEditor()
        setContent(composeRule, editor)
        val before = readCanvas(composeRule, editor)
        // Shifted by (2, 1) at scale 1, the image runs past the right and bottom document edges.
        setUnderlay(composeRule, editor) { size ->
            opaque(greenImage(WIDTH, HEIGHT), size).withPlacement(2.0, 1.0, 1.0)
        }
        val after = readCanvas(composeRule, editor)
        val samples =
            listOfNotNull(
                (after.beyondRight to after.centreY(HEIGHT - 1)).takeIf { it.first < after.width },
                (after.centreX(WIDTH - 1) to after.beyondBottom).takeIf { it.second < after.height },
            )

        TransparencyExpectation.assertArgbNear("inside", OPAQUE_GREEN, after.shownAt(WIDTH - 1, HEIGHT - 1), 0)
        assertTrue("The canvas must show surround right of or below the document", samples.isNotEmpty())
        samples.forEach { (px, py) ->
            TransparencyExpectation.assertArgbNear("surround ($px, $py)", before.at(px, py), after.at(px, py), 0)
        }
    }

    @Test
    fun theActualSizeWindowDoesNotShowTheUnderlay() {
        val editor = shownEditor { size -> opaque(greenImage(WIDTH, HEIGHT), size) }
        composeRule.runOnIdle {
            val current = editor.controller.renderState.actualSizeWindow
            val shown = if (current.visible) current else current.toggled()
            editor.controller.callbacks.onSetActualSizeWindow(shown.withScale(ActualSizeScale.X8))
        }
        val image = composeRule.onNodeWithTag(CONTENT_TAG).captureToImage().toPixelMap()
        val factor = ActualSizeScale.X8.devicePixelsPerCell
        val cellPx = TransparencyBackdrop.cellPx(composeRule.density.density)

        assertEquals("The window must show every column", WIDTH, image.width / factor)
        forEachDocumentPixel { x, y ->
            val px = x * factor + factor / 2
            val py = y * factor + factor / 2
            val expected = if (x == 0 && y == 0) OPAQUE_RED else TransparencyBackdrop.colorAt(px, py, cellPx)
            TransparencyExpectation.assertArgbNear("window ($x, $y)", expected, image[px, py].toArgb(), 0)
        }
    }

    private fun shownEditor(build: (CanvasSize) -> ReferenceUnderlay): EditorFixture {
        val editor = paintedEditor()
        setContent(composeRule, editor)
        setUnderlay(composeRule, editor, build)
        return editor
    }

    private fun assertBackdropInEmptyPixels(
        label: String,
        editor: EditorFixture,
    ) {
        val reading = readCanvas(composeRule, editor)
        forEachEmptyPixel { x, y ->
            val shown = reading.shownAt(x, y)
            TransparencyExpectation.assertArgbNear("$label ($x, $y)", reading.backdropAt(x, y), shown, 0)
        }
    }

    private companion object {
        const val HALF: Int = 128
        const val HALF_GREEN: Int = 0x8000FF00.toInt()
        const val BLEND_TOLERANCE: Int = 2

        fun opaque(
            image: ReferenceImage,
            size: CanvasSize,
        ): ReferenceUnderlay = ReferenceUnderlay.placed(image, size).withOpacity(UnderlayOpacity.MAX)

        /** Every document pixel except the painted (0, 0). */
        fun forEachEmptyPixel(action: (x: Int, y: Int) -> Unit) {
            forEachDocumentPixel { x, y -> if (x != 0 || y != 0) action(x, y) }
        }
    }
}
