package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** The library test Activity owns this runtime; it never opens the installed application's work. */
internal class OutsideCanvasPointerTest {
    @get:Rule
    val rule = createComposeRule()

    private val controller = QuickSelectFixture.controller()

    @Test
    fun exitAndReturnThroughLetterboxCommitsOneGestureWithoutOutsideBridge() {
        showCanvas()
        val initial = controller.documentState
        val canvas = rule.onNodeWithTag(CANVAS_TAG)
        canvas.performTouchInput {
            down(pixelOffset(0, 0))
            moveTo(topMargin(0))
        }
        rule.runOnIdle {
            assertNotNull(controller.renderState.preview)
            assertEquals(initial, controller.documentState)
        }
        canvas.performTouchInput {
            moveTo(topMargin(3))
            moveTo(pixelOffset(3, 0))
            up()
        }
        rule.runOnIdle {
            assertCells(setOf(0 to 0, 3 to 0))
            assertEquals(1L, controller.documentState.revision.value)
            assertNull(controller.renderState.preview)
            assertEquals(initial, controller.callbacks.onUndo().document)
            assertFalse(controller.renderState.canUndo)
        }
    }

    @Test
    fun releaseInLetterboxCommitsContainedCells() {
        showCanvas()
        rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(pixelOffset(0, 1))
            moveTo(topMargin(0))
            up()
        }
        rule.runOnIdle {
            assertCells(setOf(0 to 0, 0 to 1))
            assertEquals(1L, controller.documentState.revision.value)
            assertNull(controller.renderState.preview)
        }
    }

    @Test
    fun startInLetterboxStaysSuppressedAfterEnteringCanvas() {
        showCanvas()
        val initial = controller.documentState
        rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(topMargin(0))
            moveTo(pixelOffset(0, 0))
            up()
        }
        rule.runOnIdle {
            assertEquals(initial, controller.documentState)
            assertNull(controller.renderState.preview)
            assertFalse(controller.renderState.canUndo)
        }
    }

    @Test
    fun systemCancellationAfterExitDiscardsPreview() {
        showCanvas()
        val initial = controller.documentState
        rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(pixelOffset(0, 0))
            moveTo(topMargin(0))
            cancel()
        }
        rule.runOnIdle {
            assertEquals(initial, controller.documentState)
            assertNull(controller.renderState.preview)
            assertFalse(controller.renderState.canUndo)
        }
        rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(pixelOffset(3, 2))
            up()
        }
        rule.runOnIdle {
            assertCells(setOf(3 to 2))
            assertEquals(1L, controller.documentState.revision.value)
        }
    }

    @Test
    fun secondPointerAfterExitCancelsAndSuppressesRemainingSinglePointer() {
        showCanvas()
        val initial = controller.documentState
        rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(pointerId = 0, position = pixelOffset(0, 0))
            moveTo(pointerId = 0, position = topMargin(0))
            down(pointerId = 1, position = pixelOffset(3, 2))
            up(pointerId = 1)
            moveTo(pointerId = 0, position = pixelOffset(3, 0))
            up(pointerId = 0)
        }
        rule.runOnIdle {
            assertEquals(initial, controller.documentState)
            assertNull(controller.renderState.preview)
            assertFalse(controller.renderState.canUndo)
        }
    }

    private fun showCanvas() {
        rule.setContent {
            PixelCanvas(
                renderState = controller.renderStates.collectAsState(),
                canvasSize = controller.renderState.document.size,
                callbacks = controller.callbacks,
                committed = CommittedBitmapCache(),
                modifier = Modifier.size(SURFACE_DP.dp),
            )
        }
        rule.waitForIdle()
    }

    private fun TouchInjectionScope.pixelOffset(
        x: Int,
        y: Int,
    ): Offset {
        val surface =
            when (val result = ViewportSurface.create(width, height, rule.density.density.toDouble())) {
                is ViewportValueResult.Created -> result.value
                is ViewportValueResult.Rejected -> error("Invalid test surface: ${result.rejection}")
            }
        val transform =
            checkNotNull(
                createViewportTransform(controller.documentState.size, surface, controller.renderState.viewport),
            )
        val bounds = checkNotNull(transform.surfaceBounds(pixel(x, y)))
        return Offset(((bounds.left + bounds.right) / 2.0).toFloat(), ((bounds.top + bounds.bottom) / 2.0).toFloat())
    }

    private fun TouchInjectionScope.topMargin(x: Int): Offset {
        val centre = pixelOffset(x, 0)
        val margin = centre.y - width / DOCUMENT_WIDTH / 2f
        check(margin > 0f) { "Fixture must have actual canvas letterboxing" }
        return Offset(centre.x, margin / 2f)
    }

    private fun assertCells(covered: Set<Pair<Int, Int>>) {
        val snapshot =
            controller.documentState.frames
                .single()
                .layers
                .single()
                .snapshot
        repeat(DOCUMENT_HEIGHT) { y ->
            repeat(DOCUMENT_WIDTH) { x ->
                val expected =
                    if ((x to y) in
                        covered
                    ) {
                        PixelCell.Covered(QuickSelectFixture.index(0))
                    } else {
                        PixelCell.Empty
                    }
                assertEquals("cell ($x, $y)", expected, snapshot.cellAt(pixel(x, y)).requiredValue())
            }
        }
    }

    private fun pixel(
        x: Int,
        y: Int,
    ): PixelPosition = PixelPosition.create(PixelX.create(x).requiredValue(), PixelY.create(y).requiredValue())

    private fun <T> DomainValueResult<T>.requiredValue(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> error("Invalid test fixture: $rejection")
        }

    private companion object {
        const val CANVAS_TAG: String = "editor_canvas_4_3"
        const val DOCUMENT_WIDTH: Int = 4
        const val DOCUMENT_HEIGHT: Int = 3
        const val SURFACE_DP: Int = 240
    }
}
