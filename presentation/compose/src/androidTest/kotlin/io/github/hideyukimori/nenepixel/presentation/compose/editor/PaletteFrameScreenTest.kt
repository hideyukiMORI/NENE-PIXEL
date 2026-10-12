package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.hideyukimori.nenepixel.core.application.workspace.ActualSizeScale
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftOperation
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

internal class PaletteFrameScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun paletteDropdownKeepsTheSharedDraftAndActualSizeFollowsTheChosenFrame() {
        val controller = FrameScreenFixture.twoPaintedFrames().controller
        val frames = controller.documentState.frames
        val first = frames.first().id
        val second = frames.last().id
        controller.callbacks.frames.onSelect(first)
        controller.callbacks.onSetActualSizeWindow(
            controller.renderState.actualSizeWindow
                .toggled()
                .withScale(ActualSizeScale.X32),
        )
        FrameScreenFixture.show(rule, controller)
        assertEquals(RED, windowFirstPixel())
        click("editor_open_palette")
        click("editor_palette_editor_open")
        rule.runOnIdle {
            controller.callbacks.palette.onEdit(
                PaletteDraftOperation.SetSlotColor(PaletteIndex.first, FrameTestValues.white),
            )
        }
        val draft = controller.renderState.paletteEditSession
        val document = controller.documentState
        chooseFrame(second)
        rule.onNodeWithTag("editor_palette_editor_cancel").assertIsDisplayed()
        assertSame(draft, controller.renderState.paletteEditSession)
        assertSame(document, controller.documentState)
        assertEquals(second, controller.renderState.activeFrameId)
        val picture = CommittedBitmapCache().render(controller.renderState.picture)
        assertEquals(GREEN, picture.getPixel(0, 0))
        chooseFrame(first)
        assertSame(draft, controller.renderState.paletteEditSession)
        assertEquals(WHITE, windowFirstPixel())
        chooseFrame(second)
        click("editor_palette_editor_cancel")
        assertEquals(GREEN, windowFirstPixel())
        assertEquals(GREEN, canvasFirstPixel(controller))
    }

    private fun windowFirstPixel(): Int {
        val image = rule.onNodeWithTag("editor_actual_size_window_content").captureToImage().toPixelMap()
        return image[CELL_CENTRE, CELL_CENTRE].toArgb()
    }

    private fun canvasFirstPixel(controller: EditorController): Int {
        val image = rule.onNodeWithTag("editor_canvas_4_4").captureToImage().toPixelMap()
        val surface =
            when (
                val result =
                    ViewportSurface.create(
                        image.width,
                        image.height,
                        rule.density.density.toDouble(),
                    )
            ) {
                is ViewportValueResult.Created -> result.value
                is ViewportValueResult.Rejected -> error("Invalid frame fixture surface: ${result.rejection}")
            }
        val state = controller.renderState
        val transform = requireNotNull(createViewportTransform(state.document.size, surface, state.viewport))
        val bounds = requireNotNull(transform.surfaceBounds(FrameTestValues.position(0, 0)))
        return image[
            ((bounds.left + bounds.right) / CENTRE_DIVISOR).toInt(),
            ((bounds.top + bounds.bottom) / CENTRE_DIVISOR).toInt(),
        ].toArgb()
    }

    private fun click(tag: String) = FrameScreenFixture.click(rule, tag)

    private fun chooseFrame(id: FrameId) {
        click("editor_frame_preview")
        click("editor_palette_frame_${id.value}")
    }

    private companion object {
        const val CELL_CENTRE = 16
        const val CENTRE_DIVISOR = 2.0
        const val RED = 0xFFFF0000.toInt()
        const val GREEN = 0xFF00FF00.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
