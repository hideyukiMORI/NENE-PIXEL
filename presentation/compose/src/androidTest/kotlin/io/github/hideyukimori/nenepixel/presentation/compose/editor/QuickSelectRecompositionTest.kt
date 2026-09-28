@file:OptIn(InternalComposeTracingApi::class)

package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.util.Log
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelectItem
import io.github.hideyukimori.nenepixel.presentation.compose.editor.QuickSelectFixture.CANVAS_TAG
import io.github.hideyukimori.nenepixel.presentation.compose.editor.QuickSelectFixture.CONTROL_TAG
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * A stroke in progress never recomposes the quick-select overlay, while a highlight change does (ADR 0029
 * "Floating control" and "Enforcement impact"). The count comes from the Compose runtime's composition tracer:
 * every composable body the compiler instruments reports its name when it runs, and a skipped one does not.
 */
internal class QuickSelectRecompositionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val overlayRuns = AtomicInteger()
    private val quickSelectRuns = AtomicInteger()

    @After
    fun removeTracer() {
        composeRule.runOnIdle { Composer.setTracer(InactiveTracer) }
    }

    @Test
    fun aStrokeInProgressDoesNotRecomposeTheOverlay() {
        val controller = paintedController()
        val canvas = composeRule.onNodeWithTag(CANVAS_TAG).getUnclippedBoundsInRoot()
        val shortSide = minOf(canvas.right - canvas.left, canvas.bottom - canvas.top)
        val step = with(composeRule.density) { Offset((shortSide / STROKE_STEPS).toPx(), 0f) }
        startCounting()
        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput { down(center - step) }
        composeRule.waitForIdle()
        repeat(STROKE_MOVES) { move ->
            composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput { moveTo(center + step * move.toFloat()) }
            composeRule.waitForIdle()
        }
        val preview = controller.renderState.preview
        val overlay = overlayRuns.get()
        val quickSelect = quickSelectRuns.get()
        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput { up() }
        composeRule.waitForIdle()
        Log.i(LOG_TAG, "stroke: overlay=$overlay quickSelect=$quickSelect positions=${preview?.positionCount}")
        assertNotNull("The stroke must still be in progress before up", preview)
        assertTrue(
            "The stroke must have crossed several pixels: ${preview?.positionCount}",
            (preview?.positionCount ?: 0) >= MIN_STROKE_POSITIONS,
        )
        assertEquals("QuickSelectOverlay runs during the stroke", 0, overlay)
        assertEquals("Quick-select composable runs during the stroke", 0, quickSelect)
    }

    @Test
    fun aHighlightChangeRecomposesTheOverlay() {
        val controller = paintedController()
        composeRule.onNodeWithTag(CONTROL_TAG).performTouchInput { down(center) }
        composeRule.waitForIdle()
        val slot = QuickSelectItem.PaletteSlot(QuickSelectFixture.index(1))
        val control = composeRule.onNodeWithTag(CONTROL_TAG).getUnclippedBoundsInRoot()
        val item = composeRule.onNodeWithTag(QuickSelectSemantics.itemTag(slot)).assertIsDisplayed()
        val itemBounds = item.getUnclippedBoundsInRoot()
        val target =
            with(composeRule.density) {
                Offset(
                    ((itemBounds.left + itemBounds.right) / 2 - control.left).toPx(),
                    ((itemBounds.top + itemBounds.bottom) / 2 - control.top).toPx(),
                )
            }
        startCounting()
        composeRule.onNodeWithTag(CONTROL_TAG).performTouchInput { moveTo(target) }
        composeRule.waitForIdle()
        val overlay = overlayRuns.get()
        val quickSelect = quickSelectRuns.get()
        val menu = controller.renderState.quickSelection.menu
        val highlighted = menu?.highlighted
        composeRule.onNodeWithTag(CONTROL_TAG).performTouchInput { cancel() }
        composeRule.waitForIdle()
        Log.i(LOG_TAG, "highlight: overlay=$overlay quickSelect=$quickSelect")
        assertEquals(slot, highlighted)
        assertTrue("QuickSelectOverlay must run on a highlight change: $overlay", overlay >= 1)
    }

    private fun paintedController(): EditorController {
        val controller = QuickSelectFixture.controller()
        QuickSelectFixture.show(composeRule, controller)
        QuickSelectFixture.paint(composeRule, controller, listOf(1, 2))
        composeRule.runOnIdle { controller.callbacks.onSelectPaletteEntry(QuickSelectFixture.index(0)) }
        composeRule.waitForIdle()
        return controller
    }

    private fun startCounting() {
        composeRule.runOnIdle {
            overlayRuns.set(0)
            quickSelectRuns.set(0)
            Composer.setTracer(CountingTracer(overlayRuns, quickSelectRuns))
        }
    }

    private class CountingTracer(
        private val overlay: AtomicInteger,
        private val quickSelect: AtomicInteger,
    ) : CompositionTracer {
        override fun isTraceInProgress(): Boolean = true

        override fun traceEventStart(
            key: Int,
            dirty1: Int,
            dirty2: Int,
            info: String,
        ) {
            if (info.startsWith(OVERLAY_BODY)) overlay.incrementAndGet()
            if (info.startsWith(QUICK_SELECT_PREFIX)) quickSelect.incrementAndGet()
        }

        override fun traceEventEnd() = Unit
    }

    private object InactiveTracer : CompositionTracer {
        override fun isTraceInProgress(): Boolean = false

        override fun traceEventStart(
            key: Int,
            dirty1: Int,
            dirty2: Int,
            info: String,
        ) = Unit

        override fun traceEventEnd() = Unit
    }

    private companion object {
        const val PACKAGE: String = "io.github.hideyukimori.nenepixel.presentation.compose.editor."
        const val OVERLAY_BODY: String = PACKAGE + "QuickSelectOverlay "
        const val QUICK_SELECT_PREFIX: String = PACKAGE + "QuickSelect"
        const val STROKE_STEPS: Float = 6f
        const val STROKE_MOVES: Int = 4
        const val MIN_STROKE_POSITIONS: Int = 2
        const val LOG_TAG: String = "QuickSelectRecomposition"
    }
}
