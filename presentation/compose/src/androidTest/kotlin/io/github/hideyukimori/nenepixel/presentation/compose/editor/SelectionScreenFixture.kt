package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTransform
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues

/** A tablet-sized editor with large cells; pointer and screenshot samples use the canvas's canonical transform. */
internal object SelectionScreenFixture {
    const val CANVAS_TAG = "editor_canvas_8_6"
    const val BAR_TAG = "editor_selection_bar"
    const val SELECT_TAG = "editor_layer_select_area"

    fun show(
        rule: ComposeContentTestRule,
        onBackDispatcher: (OnBackPressedDispatcher?) -> Unit = {},
    ): EditorController {
        val controller = PresentationTestValues.fixture(PresentationTestValues.canvas(WIDTH, HEIGHT)).controller
        rule.setContent {
            onBackDispatcher(LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher)
            Box(Modifier.requiredSize(EDGE, EDGE).consumeWindowInsets(WindowInsets.safeDrawing)) {
                TestNenePixelEditor(controller, Modifier.requiredSize(EDGE, EDGE))
            }
        }
        rule.waitForIdle()
        return controller
    }

    fun pixel(
        x: Int,
        y: Int,
    ): PixelPosition = PixelPosition.create(PixelX.create(x).requiredValue(), PixelY.create(y).requiredValue())

    fun offset(
        scope: TouchInjectionScope,
        rule: ComposeContentTestRule,
        controller: EditorController,
        pixel: PixelPosition,
    ): Offset = center(transform(scope.width, scope.height, rule, controller), pixel)

    /** Centres are clear of both the selection outline and the grid; samples lie above the transient bar. */
    fun samples(
        rule: ComposeContentTestRule,
        controller: EditorController,
    ): List<Int> {
        val image = rule.onNodeWithTag(CANVAS_TAG).captureToImage().toPixelMap()
        val transform = transform(image.width, image.height, rule, controller)
        return listOf(pixel(2, 1), pixel(4, 1), pixel(5, 1)).map {
            val point = center(transform, it)
            image[point.x.toInt(), point.y.toInt()].toArgb()
        }
    }

    private fun transform(
        width: Int,
        height: Int,
        rule: ComposeContentTestRule,
        controller: EditorController,
    ): ViewportTransform {
        val surface = ViewportSurface.create(width, height, rule.density.density.toDouble()).requiredValue()
        val state = controller.renderState
        return requireNotNull(createViewportTransform(state.document.size, surface, state.viewport))
    }

    private fun center(
        transform: ViewportTransform,
        pixel: PixelPosition,
    ): Offset {
        val bounds = requireNotNull(transform.surfaceBounds(pixel))
        return Offset(
            ((bounds.left + bounds.right) / DIVISOR).toFloat(),
            ((bounds.top + bounds.bottom) / DIVISOR).toFloat(),
        )
    }

    private fun <T> DomainValueResult<T>.requiredValue(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> error("Invalid selection fixture pixel: $rejection")
        }

    private fun <T> ViewportValueResult<T>.requiredValue(): T =
        when (this) {
            is ViewportValueResult.Created -> value
            is ViewportValueResult.Rejected -> error("Invalid selection fixture surface: $rejection")
        }

    private val EDGE = 600.dp
    private const val WIDTH = 8
    private const val HEIGHT = 6
    private const val DIVISOR = 2.0
}
