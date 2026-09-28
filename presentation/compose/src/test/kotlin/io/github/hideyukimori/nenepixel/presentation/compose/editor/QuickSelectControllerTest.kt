package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.CanvasPointerIntent
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.EyedropperState
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelectItem
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfacePoint
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.EditorFixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.colorAt
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.position
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.white
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class QuickSelectControllerTest {
    private val surface: ViewportSurface = viewportSurface()

    @Test
    fun `confirming a highlighted recent slot selects it and closes the menu`() {
        val fixture = fixture()
        paint(fixture, paletteIndex(1), 0, 0)
        paint(fixture, paletteIndex(0), 1, 0)
        val quickSelect = fixture.controller.callbacks.quickSelect

        val opened = quickSelect.onOpen()
        assertEquals(
            listOf(slot(0), slot(1), QuickSelectItem.Eyedropper),
            opened.quickSelection.menu?.items,
        )
        val highlighted = quickSelect.onHighlight(slot(1))
        assertEquals(slot(1), highlighted.quickSelection.menu?.highlighted)
        assertEquals(paletteIndex(0), highlighted.activePaletteIndex)

        val confirmed = quickSelect.onConfirm()

        assertEquals(paletteIndex(1), confirmed.activePaletteIndex)
        assertNull(confirmed.quickSelection.menu)
        assertSame(confirmed, fixture.controller.renderState)
    }

    @Test
    fun `confirming the eyedropper arms the pick intent`() {
        val fixture = fixture()

        val armed = arm(fixture)

        assertEquals(EyedropperState.Armed, armed.quickSelection.eyedropper)
        assertNull(armed.quickSelection.menu)
        assertEquals(CanvasPointerIntent.PickPaletteEntry, fixture.controller.workspaceState.canvasPointerIntent)
    }

    @Test
    fun `armed pointer down picks the painted slot index without starting a stroke`() {
        val fixture = fixture()
        paint(fixture, paletteIndex(3), 1, 1)
        fixture.controller.callbacks.onSelectPaletteEntry(paletteIndex(0))
        val beforePick = fixture.controller.documentState
        arm(fixture)

        val picked = fixture.controller.pointerDown(surface, surfacePoint(1, 1))

        val ignored = assertInstanceOf(PointerInputAcknowledgement.Ignored::class.java, picked)
        assertEquals(paletteIndex(3), ignored.renderState.activePaletteIndex)
        assertEquals(EyedropperState.Idle, ignored.renderState.quickSelection.eyedropper)
        assertNull(ignored.renderState.preview)
        assertSame(beforePick, fixture.controller.documentState)
        assertEquals(CanvasPointerIntent.Draw, fixture.controller.workspaceState.canvasPointerIntent)
        assertSame(ignored.renderState, fixture.controller.renderState)
    }

    @Test
    fun `picking between two slots of the same rgba selects the painted one`() {
        val fixture = fixture()
        assertEquals(white, entryColor(fixture, 2))
        assertEquals(white, entryColor(fixture, 4))
        paint(fixture, paletteIndex(4), 2, 2)
        paint(fixture, paletteIndex(2), 3, 3)
        assertEquals(white, colorAt(fixture.controller.documentState, position(2, 2)))
        arm(fixture)

        val picked = fixture.controller.pointerDown(surface, surfacePoint(2, 2))

        val ignored = assertInstanceOf(PointerInputAcknowledgement.Ignored::class.java, picked)
        assertEquals(paletteIndex(4), ignored.renderState.activePaletteIndex)
    }

    @Test
    fun `armed pointer down outside the canvas is rejected and stays armed`() {
        val fixture = fixture()
        val armed = arm(fixture)

        val outside = fixture.controller.pointerDown(surface, surfacePoint(SURFACE_EDGE, CELL_EDGE / 2.0))

        val rejected = assertInstanceOf(PointerInputAcknowledgement.Rejected::class.java, outside)
        assertEquals(EyedropperState.Armed, rejected.renderState.quickSelection.eyedropper)
        assertEquals(armed.activePaletteIndex, rejected.renderState.activePaletteIndex)
        assertNull(rejected.renderState.preview)
    }

    @Test
    fun `pointer down while the menu is open is rejected without a preview`() {
        val fixture = fixture()
        fixture.controller.callbacks.quickSelect
            .onOpen()
        assertEquals(CanvasPointerIntent.Draw, fixture.controller.workspaceState.canvasPointerIntent)

        val down = fixture.controller.pointerDown(surface, surfacePoint(0, 0))

        val rejected = assertInstanceOf(PointerInputAcknowledgement.Rejected::class.java, down)
        assertNull(rejected.renderState.preview)
        assertNotNull(rejected.renderState.quickSelection.menu)
        assertSame(fixture.initialDocument, fixture.controller.documentState)
    }

    @Test
    fun `cancel closes the menu without selecting and disarm returns to drawing`() {
        val fixture = fixture()
        val quickSelect = fixture.controller.callbacks.quickSelect
        val initialIndex = fixture.controller.renderState.activePaletteIndex
        quickSelect.onOpen()
        quickSelect.onHighlight(QuickSelectItem.Eyedropper)

        val cancelled = quickSelect.onCancel()

        assertNull(cancelled.quickSelection.menu)
        assertEquals(EyedropperState.Idle, cancelled.quickSelection.eyedropper)
        assertEquals(initialIndex, cancelled.activePaletteIndex)

        arm(fixture)
        val disarmed = quickSelect.onDisarm()

        assertEquals(EyedropperState.Idle, disarmed.quickSelection.eyedropper)
        assertEquals(CanvasPointerIntent.Draw, fixture.controller.workspaceState.canvasPointerIntent)
        assertSame(disarmed, fixture.controller.renderState)
    }

    private fun arm(fixture: EditorFixture): EditorRenderState {
        val quickSelect = fixture.controller.callbacks.quickSelect
        quickSelect.onOpen()
        quickSelect.onHighlight(QuickSelectItem.Eyedropper)
        return quickSelect.onConfirm()
    }

    private fun paint(
        fixture: EditorFixture,
        index: PaletteIndex,
        x: Int,
        y: Int,
    ) {
        fixture.controller.callbacks.onSelectPaletteEntry(index)
        fixture.controller.pointerDown(surface, surfacePoint(x, y))
        assertInstanceOf(
            PointerInputAcknowledgement.Accepted::class.java,
            fixture.controller.pointerEnd(surface, surfacePoint(x, y)),
        )
    }

    private fun entryColor(
        fixture: EditorFixture,
        index: Int,
    ): PixelColor =
        when (val entry = entryAt(fixture, paletteIndex(index))) {
            is DomainValueResult.Created -> entry.value.color
            is DomainValueResult.Rejected -> fail("Palette entry fixture was rejected: ${entry.rejection}")
        }

    private fun entryAt(
        fixture: EditorFixture,
        index: PaletteIndex,
    ) = fixture.controller.renderState.palette
        .entryAt(index)

    private fun slot(value: Int): QuickSelectItem = QuickSelectItem.PaletteSlot(paletteIndex(value))

    private fun paletteIndex(value: Int): PaletteIndex =
        when (val result = PaletteIndex.create(value)) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Palette index fixture was rejected: ${result.rejection}")
        }

    private fun viewportSurface(): ViewportSurface =
        ViewportSurface.create(SURFACE_EDGE.toInt(), SURFACE_EDGE.toInt(), PIXELS_PER_DP).requiredValue()

    private fun surfacePoint(
        x: Int,
        y: Int,
    ): ViewportSurfacePoint = surfacePoint((x + HALF_CELL) * CELL_EDGE, (y + HALF_CELL) * CELL_EDGE)

    private fun surfacePoint(
        xPixels: Double,
        yPixels: Double,
    ): ViewportSurfacePoint = ViewportSurfacePoint.create(xPixels, yPixels).requiredValue()

    private fun <T> ViewportValueResult<T>.requiredValue(): T =
        when (this) {
            is ViewportValueResult.Created -> value
            is ViewportValueResult.Rejected -> fail("Viewport test value was rejected: $rejection")
        }

    private companion object {
        const val CELL_EDGE: Double = 100.0
        const val HALF_CELL: Double = 0.5
        const val SURFACE_EDGE: Double = 400.0
        const val PIXELS_PER_DP: Double = 2.0
    }
}
