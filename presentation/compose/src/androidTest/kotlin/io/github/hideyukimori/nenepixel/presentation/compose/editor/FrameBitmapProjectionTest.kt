package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftOperation
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/** Android bitmap identity and colour truth across same-document frame focus, including shared palette drafts. */
internal class FrameBitmapProjectionTest {
    @Test
    fun frameFocusInvalidatesOnlyTheCurrentBitmapAndDraftRecoloursTheChosenFrame() {
        val controller = FrameScreenFixture.twoPaintedFrames().controller
        val first =
            controller.documentState.frames
                .first()
                .id
        val second =
            controller.documentState.frames
                .last()
                .id
        val cache = CommittedBitmapCache()
        val green = cache.render(controller.renderState.picture)
        assertEquals(GREEN, green.getPixel(0, 0))
        assertSame(green, cache.render(controller.renderState.picture))
        controller.callbacks.frames.onSelect(first)
        val red = cache.render(controller.renderState.picture)
        assertNotSame(green, red)
        assertEquals(RED, red.getPixel(0, 0))
        controller.callbacks.palette.onBegin()
        controller.callbacks.palette.onEdit(
            PaletteDraftOperation.SetSlotColor(PaletteIndex.first, FrameTestValues.white),
        )
        assertEquals(WHITE, cache.render(controller.renderState.picture).getPixel(0, 0))
        controller.callbacks.frames.onSelect(second)
        assertEquals(GREEN, cache.render(controller.renderState.picture).getPixel(0, 0))
        controller.callbacks.frames.onSelect(first)
        assertEquals(WHITE, cache.render(controller.renderState.picture).getPixel(0, 0))
        controller.callbacks.palette.onCancel()
        assertEquals(RED, cache.render(controller.renderState.picture).getPixel(0, 0))
    }

    private companion object {
        const val RED = 0xFFFF0000.toInt()
        const val GREEN = 0xFF00FF00.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
