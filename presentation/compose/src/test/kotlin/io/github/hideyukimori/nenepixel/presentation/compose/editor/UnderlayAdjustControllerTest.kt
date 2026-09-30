package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.CanvasPointerIntent
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.EyedropperState
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelectItem
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayPlacement
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfacePoint
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.EditorFixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

/**
 * The adjust-mode pointer route of ADR 0032 through [EditorController]. The 4x4 document fills the 400x400 surface,
 * so one document pixel is 100 surface px; the 2x2 image fits at scale 2 with its corner at (0, 0).
 */
internal class UnderlayAdjustControllerTest {
    private val surface: ViewportSurface = ViewportSurface.create(400, 400, PIXELS_PER_DP).requiredValue()

    @Test
    fun `a one-pointer drag moves the underlay and nothing else`() {
        val fixture = fixture()
        adjust(fixture)
        val before = fixture.controller.renderState

        val down = fixture.controller.pointerDown(surface, point(150.0, 150.0))
        val moved = fixture.controller.pointerMove(surface, point(250.0, 200.0))

        assertInstanceOf(PointerInputAcknowledgement.Accepted::class.java, down)
        assertSame(before.document, down.renderState.document)
        val after = assertInstanceOf(PointerInputAcknowledgement.Accepted::class.java, moved).renderState
        assertPlacement(after.underlay?.placement, left = 1.0, top = 0.5, scale = 2.0)
        assertSame(fixture.initialDocument, fixture.runtime.state.documentState)
        assertSame(before.document, after.document)
        assertEquals(before.canUndo, after.canUndo)
        assertEquals(before.canRedo, after.canRedo)
        assertEquals(before.dirtyState, after.dirtyState)
        assertSame(before.viewport, after.viewport)
        assertNull(after.preview)
        assertSame(after, fixture.controller.renderState)
    }

    @Test
    fun `end applies the last move and cancel keeps the moved position, both forgetting the point`() {
        val fixture = fixture()
        adjust(fixture)
        fixture.controller.pointerDown(surface, point(0.0, 0.0))
        fixture.controller.pointerMove(surface, point(100.0, 0.0))
        val ended = fixture.controller.pointerEnd(surface, point(100.0, 100.0))
        assertPlacement(ended.renderState.underlay?.placement, left = 1.0, top = 1.0, scale = 2.0)
        assertInstanceOf(
            PointerInputAcknowledgement.Ignored::class.java,
            fixture.controller.pointerMove(surface, point(300.0, 300.0)),
        )

        fixture.controller.pointerDown(surface, point(0.0, 0.0))
        fixture.controller.pointerMove(surface, point(-100.0, 0.0))
        val cancelled = fixture.controller.pointerCancel()
        assertInstanceOf(PointerInputAcknowledgement.Ignored::class.java, cancelled)
        assertPlacement(cancelled.renderState.underlay?.placement, left = 0.0, top = 1.0, scale = 2.0)
        val unmoved = fixture.controller.pointerMove(surface, point(300.0, 300.0))
        assertInstanceOf(PointerInputAcknowledgement.Ignored::class.java, unmoved)
        assertPlacement(unmoved.renderState.underlay?.placement, left = 0.0, top = 1.0, scale = 2.0)
    }

    @Test
    fun `a two-pointer gesture scales the underlay about the centroid and keeps the viewport`() {
        val fixture = fixture()
        adjust(fixture)
        val viewport = fixture.controller.renderState.viewport
        fixture.controller.pointerDown(surface, point(150.0, 200.0))

        val started = fixture.controller.viewportStarted(surface)
        // Centroid (200, 200) is document (2, 2); the pointer distance doubles.
        val gesture =
            ViewportGesture.create(point(150.0, 200.0), point(250.0, 200.0), point(100.0, 200.0), point(300.0, 200.0))
        val transformed = fixture.controller.viewportTransformed(surface, gesture)

        assertInstanceOf(PointerInputAcknowledgement.Ignored::class.java, started)
        assertSame(viewport, started.renderState.viewport)
        assertInstanceOf(PointerInputAcknowledgement.Accepted::class.java, transformed)
        assertPlacement(transformed.renderState.underlay?.placement, left = -2.0, top = -2.0, scale = 4.0)
        assertSame(viewport, transformed.renderState.viewport)
        assertSame(viewport, fixture.runtime.state.workspaceState.viewport)
        assertSame(fixture.initialDocument, fixture.runtime.state.documentState)
    }

    @Test
    fun `a rested underlay returns the canvas to drawing, also in the middle of a drag`() {
        val fixture = fixture()
        val adjusting = adjust(fixture)
        fixture.controller.pointerDown(surface, point(150.0, 150.0))
        val callbacks = fixture.controller.callbacks
        callbacks.underlay.onSet(adjusting.rested())

        // The move takes the drawing route: there is no stroke to extend, and the underlay stays.
        val move = fixture.controller.pointerMove(surface, point(250.0, 250.0))
        assertNull(move.renderState.preview)
        assertPlacement(move.renderState.underlay?.placement, left = 0.0, top = 0.0, scale = 2.0)

        val down = fixture.controller.pointerDown(surface, point(150.0, 150.0))
        assertInstanceOf(PointerInputAcknowledgement.Accepted::class.java, down)
        assertNotNull(down.renderState.preview)
        assertEquals(CanvasPointerIntent.Draw, fixture.controller.workspaceState.canvasPointerIntent)
    }

    @Test
    fun `adjusting wins over an armed eyedropper, which picks after resting`() {
        val fixture = fixture()
        // A painted cell to pick: a blank cell has no palette entry to pick.
        fixture.controller.pointerDown(surface, point(150.0, 150.0))
        fixture.controller.pointerEnd(surface, point(150.0, 150.0))
        val quickSelect = fixture.controller.callbacks.quickSelect
        quickSelect.onOpen()
        quickSelect.onHighlight(QuickSelectItem.Eyedropper)
        quickSelect.onConfirm()
        val adjusting = adjust(fixture)

        fixture.controller.pointerDown(surface, point(150.0, 150.0))
        val moved = fixture.controller.pointerMove(surface, point(250.0, 150.0))
        assertPlacement(moved.renderState.underlay?.placement, left = 1.0, top = 0.0, scale = 2.0)
        assertEquals(EyedropperState.Armed, moved.renderState.quickSelection.eyedropper)

        val callbacks = fixture.controller.callbacks
        callbacks.underlay.onSet(requireNotNull(moved.renderState.underlay).rested())
        val picked = fixture.controller.pointerDown(surface, point(150.0, 150.0))
        assertInstanceOf(PointerInputAcknowledgement.Ignored::class.java, picked)
        assertEquals(EyedropperState.Idle, picked.renderState.quickSelection.eyedropper)
        assertNull(picked.renderState.preview)
        assertEquals(adjusting.image, picked.renderState.underlay?.image)
    }

    @Test
    fun `a pointer down outside the document still moves the underlay`() {
        val fixture = fixture()
        adjust(fixture)

        val down = fixture.controller.pointerDown(surface, point(400.0, 50.0))
        val moved = fixture.controller.pointerMove(surface, point(500.0, 100.0))

        assertInstanceOf(PointerInputAcknowledgement.Accepted::class.java, down)
        assertPlacement(moved.renderState.underlay?.placement, left = 1.0, top = 0.5, scale = 2.0)
        assertNull(moved.renderState.preview)
    }

    private fun adjust(fixture: EditorFixture): ReferenceUnderlay {
        val created = ReferenceImage.create(2, 2, IntArray(4) { OPAQUE_BLACK })
        val image = (created as ReferenceImageResult.Created).image
        val adjusting = ReferenceUnderlay.placed(image, fixture.controller.renderState.document.size).adjusting()
        val callbacks = fixture.controller.callbacks
        callbacks.underlay.onSet(adjusting)
        assertEquals(CanvasPointerIntent.AdjustUnderlay, fixture.controller.workspaceState.canvasPointerIntent)
        return adjusting
    }

    private fun assertPlacement(
        placement: UnderlayPlacement?,
        left: Double,
        top: Double,
        scale: Double,
    ) {
        val actual = requireNotNull(placement)
        assertEquals(left, actual.left, EPSILON)
        assertEquals(top, actual.top, EPSILON)
        assertEquals(scale, actual.scale, EPSILON)
    }

    private fun point(
        xPixels: Double,
        yPixels: Double,
    ): ViewportSurfacePoint = ViewportSurfacePoint.create(xPixels, yPixels).requiredValue()

    private fun <T> ViewportValueResult<T>.requiredValue(): T =
        when (this) {
            is ViewportValueResult.Created -> value
            is ViewportValueResult.Rejected -> fail("Viewport test value was rejected: $rejection")
        }

    private companion object {
        const val PIXELS_PER_DP: Double = 2.0
        const val OPAQUE_BLACK: Int = 0x000000FF
        const val EPSILON: Double = 1e-9
    }
}
