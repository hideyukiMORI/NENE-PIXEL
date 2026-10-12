package io.github.hideyukimori.nenepixel.core.application.render

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.blackIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.greenIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.snapshot
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.frame
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.id
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.required
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.runtime
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layer
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftOperation
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class FrameCompositeTest {
    @Test fun `same document explicit frame query renders only that stack and missing identity is typed`() {
        val document = document()
        assertEquals(listOf(0xFF0000FF.toInt(), 0x000000FF), image(document, 1).copyPackedRgba8888().toList())
        assertEquals(listOf(0x00FF00FF, 0x00FF00FF), image(document, 2).copyPackedRgba8888().toList())
        assertEquals(DocumentCompositeRenderResult.FrameNotFound(id(99)), DocumentComposite.render(document, id(99)))
    }

    @Test fun `shared palette draft frame changes invalidate every image key and retain only one preparation`() {
        val document = document()
        val runtime = runtime(document)
        runtime.paletteOperations.beginPaletteEdit()
        runtime.reduce(WorkspaceAction.EditPaletteDraft(PaletteDraftOperation.SetSlotColor(redIndex, green)))
        val session = requireNotNull(runtime.state.workspaceState.paletteEditSession)
        val cache = PaletteDraftCompositeCache()
        val first = cache.render(document, id(1), document.definition, session)
        assertSame(first, cache.render(document, id(1), document.definition, session))
        assertEquals(1, cache.preparationCount)
        val second = cache.render(document, id(2), document.definition, session)
        assertNotSame(first, second)
        assertEquals(2, cache.preparationCount)
        val expected =
            assertInstanceOf(
                PaletteDraftCompositeResult.Rendered::class.java,
                PaletteDraftComposite.render(document, id(2), session),
            ).image
        assertArrayEquals(expected.copyPackedRgba8888(), second.copyPackedRgba8888())
        assertArrayEquals(
            intArrayOf(0x00FF00FF, 0x000000FF),
            cache.render(document, id(1), document.definition, session).copyPackedRgba8888(),
        )
        assertEquals(3, cache.preparationCount)
    }

    @Test fun `preview preparation requires the target owning frame and matches its committed counterpart`() {
        val document = document()
        val runtime = runtime(document)
        runtime.reduce(WorkspaceAction.SelectFrame(id(2)))
        runtime.reduce(WorkspaceAction.BeginGesturePreview(document.size, position(0, 0)))
        val gesture = requireNotNull(runtime.state.workspaceState.preview)
        assertThrows(IllegalStateException::class.java) { StrokePreviewComposite.prepare(document, id(1), gesture) }
        val preview = StrokePreviewComposite.prepare(document, id(2), gesture)
        runtime.execute(
            ApplyStrokeCommand.create(
                runtime.captureSource(),
                layerId(9),
                stroke(document.size, listOf(position(0, 0)), blackIndex),
            ),
        )
        assertEquals(
            image(runtime.state.documentState, 2).copyPackedRgba8888()[0],
            preview.packedRgba8888At(position(0, 0)),
        )
        val source = required(PixelRegion.create(document.size, position(0, 0), canvas(1, 1)))
        assertThrows(IllegalArgumentException::class.java) {
            SelectionPreviewComposite.prepare(document, id(2), layerId(1), source)
        }
    }

    private fun document(): DocumentState =
        state(
            frame(1, listOf(layer(1, "a", snapshot(canvas(2, 1), listOf(redIndex, blackIndex))))),
            frame(2, listOf(layer(9, "b", snapshot(canvas(2, 1), listOf(greenIndex, greenIndex))))),
        )

    private fun image(
        document: DocumentState,
        frame: Int,
    ): DocumentCompositeImage =
        assertInstanceOf(
            DocumentCompositeRenderResult.Rendered::class.java,
            DocumentComposite.render(document, id(frame)),
        ).image
}
