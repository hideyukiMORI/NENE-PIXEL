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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.presentation.compose.editor.QuickSelectFixture.CANVAS_TAG
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A stroke, from down to up, never recomposes the layer chip, nor the open panel with its rows, row menus and add
 * row, while a visibility change does (#144 UI spec "状態の持ち方" and "Verification"). The count comes from the
 * Compose runtime's composition tracer, as in [QuickSelectRecompositionTest]: every composable body the compiler
 * instruments reports its name when it runs, and a skipped one does not.
 */
internal class LayerRecompositionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val overlayRuns = AtomicInteger()
    private val layerRuns = AtomicInteger()
    private val layerNames: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @After
    fun removeTracer() {
        composeRule.runOnIdle { Composer.setTracer(InactiveTracer) }
    }

    @Test
    fun aStrokeWithThePanelClosedDoesNotRecomposeTheChip() {
        val controller = shownController()
        composeRule.onNodeWithTag(CHIP_TAG).assertIsDisplayed()
        startCounting()
        val positions = drawStroke(controller)
        assertNoLayerRuns("closed", positions)
    }

    @Test
    fun aStrokeWithThePanelOpenDoesNotRecomposeThePanel() {
        val controller = shownController()
        composeRule.onNodeWithTag(CHIP_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PANEL_TAG).assertIsDisplayed()
        startCounting()
        val positions = drawStroke(controller)
        assertNoLayerRuns("open", positions)
    }

    @Test
    fun aVisibilityChangeRecomposesThePanel() {
        val controller = shownController()
        composeRule.onNodeWithTag(CHIP_TAG).performClick()
        composeRule.waitForIdle()
        val active = controller.renderState.activeLayerId
        startCounting()
        composeRule.onNodeWithTag(VISIBILITY_TAG_PREFIX + active.value).performClick()
        composeRule.waitForIdle()
        val overlay = overlayRuns.get()
        val layer = layerRuns.get()
        Log.i(LOG_TAG, "toggle: overlay=$overlay layer=$layer names=$layerNames")
        val layers = controller.renderState.document.layers
        assertEquals(LayerVisibility.Hidden, layers.first { layer -> layer.id == active }.visibility)
        assertTrue("LayerOverlay must run on a visibility change: $overlay", overlay >= 1)
        assertTrue("Layer composables must run on a visibility change: $layer", layer >= 1)
    }

    /** Shows the editor with [LAYERS] layers, so the open panel lists several rows with their menus. */
    private fun shownController(): EditorController {
        val controller = QuickSelectFixture.controller()
        QuickSelectFixture.show(composeRule, controller)
        composeRule.runOnIdle { repeat(LAYERS - 1) { controller.callbacks.layers.onAdd() } }
        composeRule.waitForIdle()
        assertEquals(LAYERS, controller.renderState.document.layers.size)
        return controller
    }

    /**
     * Draws one stroke rightwards from the canvas centre, clear of the panel in the top-left corner: down, a few
     * moves, then up. Returns the positions the stroke crossed before up.
     */
    private fun drawStroke(controller: EditorController): Int {
        val canvas = composeRule.onNodeWithTag(CANVAS_TAG).getUnclippedBoundsInRoot()
        val shortSide = minOf(canvas.right - canvas.left, canvas.bottom - canvas.top)
        val step = with(composeRule.density) { Offset((shortSide / STROKE_STEPS).toPx(), 0f) }
        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput { down(center) }
        composeRule.waitForIdle()
        repeat(STROKE_MOVES) { move ->
            composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput { moveTo(center + step * (move + 1).toFloat()) }
            composeRule.waitForIdle()
        }
        val positions = controller.renderState.preview?.positionCount ?: 0
        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput { up() }
        composeRule.waitForIdle()
        return positions
    }

    private fun assertNoLayerRuns(
        name: String,
        positions: Int,
    ) {
        val overlay = overlayRuns.get()
        val layer = layerRuns.get()
        Log.i(LOG_TAG, "$name stroke: overlay=$overlay layer=$layer positions=$positions names=$layerNames")
        assertTrue("The stroke must have crossed several pixels: $positions", positions >= MIN_STROKE_POSITIONS)
        assertEquals("LayerOverlay runs during the $name stroke", 0, overlay)
        assertEquals("Layer composable runs during the $name stroke: $layerNames", 0, layer)
    }

    private fun startCounting() {
        composeRule.runOnIdle {
            overlayRuns.set(0)
            layerRuns.set(0)
            layerNames.clear()
            Composer.setTracer(CountingTracer(overlayRuns, layerRuns, layerNames))
        }
    }

    private class CountingTracer(
        private val overlay: AtomicInteger,
        private val layer: AtomicInteger,
        private val names: MutableSet<String>,
    ) : CompositionTracer {
        override fun isTraceInProgress(): Boolean = true

        override fun traceEventStart(
            key: Int,
            dirty1: Int,
            dirty2: Int,
            info: String,
        ) {
            if (info.startsWith(OVERLAY_BODY)) overlay.incrementAndGet()
            if (info.startsWith(LAYER_PREFIX)) {
                layer.incrementAndGet()
                names.add(info.substringBefore(' '))
            }
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
        const val OVERLAY_BODY: String = PACKAGE + "LayerOverlay "
        const val LAYER_PREFIX: String = PACKAGE + "Layer"
        const val CHIP_TAG: String = "editor_layer_chip"
        const val PANEL_TAG: String = "editor_layer_panel"
        const val VISIBILITY_TAG_PREFIX: String = "editor_layer_visibility_"
        const val LAYERS: Int = 3
        const val STROKE_STEPS: Float = 8f
        const val STROKE_MOVES: Int = 3
        const val MIN_STROKE_POSITIONS: Int = 2
        const val LOG_TAG: String = "LayerRecomposition"
    }
}
