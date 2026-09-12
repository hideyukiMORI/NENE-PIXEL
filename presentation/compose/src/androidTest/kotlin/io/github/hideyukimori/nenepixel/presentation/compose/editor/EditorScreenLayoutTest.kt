package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorControlEdge
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorLayout
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorTheme
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportZoom
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The surface fills the remaining window; ADR 0004 owns aspect fit inside that surface. */
internal class EditorScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun landscapeAllocatesCanvasSpaceAndKeepsBottomControlsReachable() {
        setEditorContent(controller(), 600.dp, 400.dp)
        val canvas = canvasBounds()
        assertTrue(
            "600x400dp must keep at least 250dp for drawing: $canvas",
            (canvas.bottom - canvas.top).value >= 250f,
        )
        assertContained(rootBounds(), canvas)
        dockBounds().forEach { control ->
            assertContained(rootBounds(), control)
            assertTrue("Tools must be below canvas", control.top >= canvas.bottom)
            assertTrue("Touch target height", (control.bottom - control.top).value >= 48f)
        }
        assertEquals(600f, (canvas.right - canvas.left).value, 1f)
    }

    @Test
    fun portraitAllocatesTheRemainingSurfaceAndKeepsFileActionsReachable() {
        setEditorContent(controller(), 600.dp, 900.dp)
        val canvas = canvasBounds()
        assertTrue("Portrait canvas height", (canvas.bottom - canvas.top).value >= 750f)
        assertContained(rootBounds(), canvas)
        dockBounds().forEach { assertContained(rootBounds(), it) }
        composeRule.onNodeWithContentDescription("File").performClick()
        listOf("Save As", "Load", "New document", "Export PNG").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        composeRule.onNodeWithContentDescription("Close panel").performClick()
        composeRule.onNodeWithText("No unsaved changes").assertIsDisplayed()
    }

    @Test
    fun handheldSupportsBothPhysicalEdgesWithoutDocumentChanges() {
        val controller = controller()
        setEditorContent(controller, 600.dp, 400.dp)
        val initial = controller.renderState
        EditorControlEdge.entries.forEach { edge ->
            composeRule.runOnIdle {
                controller.callbacks.onSetAppearance(
                    initial.appearance.copy(layout = EditorLayout.Handheld, controlEdge = edge),
                )
            }
            val canvas = canvasBounds()
            assertTrue((canvas.bottom - canvas.top).value >= 310f)
            assertTrue((canvas.right - canvas.left).value >= 520f)
            assertContained(rootBounds(), canvas)
            dockBounds().forEach { control ->
                assertContained(rootBounds(), control)
                when (edge) {
                    EditorControlEdge.Left -> assertTrue(control.right <= canvas.left)
                    EditorControlEdge.Right -> assertTrue(control.left >= canvas.right)
                }
            }
            assertSame(initial.snapshot, controller.renderState.snapshot)
            assertEquals(initial.viewport, controller.renderState.viewport)
        }
    }

    @Test
    fun appearanceAndPalettePanelsUseTheCanonicalCallbacks() {
        val controller = controller()
        setEditorContent(controller, 600.dp, 400.dp)
        val before = controller.renderState.snapshot
        composeRule.onNodeWithContentDescription("Appearance").performClick()
        composeRule.onNodeWithContentDescription("Theme: Light").performClick()
        composeRule.onNodeWithContentDescription("Theme: Light").assertIsSelected()
        composeRule.onNodeWithContentDescription("Layout: Handheld").performClick()
        composeRule.onNodeWithContentDescription("Control edge: Left").performClick()
        composeRule.onNodeWithContentDescription("Close panel").performClick()
        assertEquals(EditorTheme.Light, controller.renderState.appearance.theme)
        assertEquals(EditorLayout.Handheld, controller.renderState.appearance.layout)
        assertEquals(EditorControlEdge.Left, controller.renderState.appearance.controlEdge)
        composeRule.onNodeWithContentDescription("Open palette").performClick()
        composeRule.onNodeWithContentDescription(FIRST_PALETTE_DESCRIPTION).assertIsSelected().performClick()
        composeRule.onNodeWithContentDescription("Close panel").assertDoesNotExist()
        assertSame(before, controller.renderState.snapshot)
    }

    @Test
    fun zoomedCanvasCannotPaintOverTheLeftDock() {
        val controller = controller()
        val appearance =
            controller.renderState.appearance.copy(
                layout = EditorLayout.Handheld,
                controlEdge = EditorControlEdge.Left,
            )
        controller.callbacks.onSetAppearance(appearance)
        setEditorContent(controller, 600.dp, 400.dp)
        val root = rootBounds()
        val tool = composeRule.onNodeWithContentDescription("Pencil tool").getUnclippedBoundsInRoot()
        val x = with(composeRule.density) { (tool.right - root.left - 2.dp).roundToPx() }
        val y = with(composeRule.density) { ((tool.top + tool.bottom) / 2 - root.top).roundToPx() }
        val before = composeRule.onNodeWithTag(ROOT_TAG).captureToImage().toPixelMap()[x, y]
        composeRule.runOnIdle {
            val zoom =
                when (val result = ViewportZoom.create(4.0)) {
                    is ViewportValueResult.Created -> result.value
                    is ViewportValueResult.Rejected -> error("Invalid test zoom")
                }
            val viewport = ViewportState.create(zoom, controller.renderState.viewport.center)
            controller.runtime.reduce(WorkspaceAction.SetViewport(viewport))
            controller.synchronizeWithRuntime()
        }
        val after = composeRule.onNodeWithTag(ROOT_TAG).captureToImage().toPixelMap()[x, y]
        assertEquals("Clipping must preserve the previously drawn left dock", before, after)
    }

    @Test
    fun largestSupportedToolPaletteCanSelectAndReopenItsLastEntry() {
        val controller = controller(paletteCount = 32)
        setEditorContent(controller, 600.dp, 400.dp)
        val before = controller.renderState.snapshot
        composeRule.onNodeWithContentDescription("Open palette").performClick()
        composeRule.onNodeWithContentDescription("Palette colors").performScrollToIndex(31)
        composeRule.onNodeWithContentDescription(LAST_PALETTE_DESCRIPTION).assertIsDisplayed().performClick()
        assertEquals(31, controller.renderState.activePaletteIndex.value)
        assertSame(before, controller.renderState.snapshot)
        composeRule.onNodeWithContentDescription("Open palette").performClick()
        composeRule.onNodeWithContentDescription(LAST_PALETTE_DESCRIPTION).assertIsDisplayed().assertIsSelected()
    }

    private fun setEditorContent(
        controller: EditorController,
        width: Dp,
        height: Dp,
    ) {
        composeRule.setContent {
            Box(Modifier.requiredSize(width, height).consumeWindowInsets(WindowInsets.safeDrawing).testTag(ROOT_TAG)) {
                TestNenePixelEditor(controller, Modifier.requiredSize(width, height))
            }
        }
    }

    private fun rootBounds(): DpRect = composeRule.onNodeWithTag(ROOT_TAG).getUnclippedBoundsInRoot()

    private fun canvasBounds(): DpRect =
        composeRule
            .onNodeWithContentDescription(CANVAS_DESCRIPTION)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()

    private fun dockBounds(): List<DpRect> =
        listOf("Pencil tool", "Eraser tool", "Undo", "Redo", "Open palette").map {
            composeRule.onNodeWithContentDescription(it).assertIsDisplayed().getUnclippedBoundsInRoot()
        }

    private fun assertContained(
        outer: DpRect,
        inner: DpRect,
    ) {
        assertTrue(
            "Bounds $inner outside $outer",
            inner.left >= outer.left && inner.top >= outer.top &&
                inner.right <= outer.right && inner.bottom <= outer.bottom,
        )
    }

    private fun controller(paletteCount: Int = 1): EditorController {
        val size =
            CanvasSize.create(
                CanvasWidth.create(DOCUMENT_WIDTH).requiredValue(),
                CanvasHeight.create(DOCUMENT_HEIGHT).requiredValue(),
            )
        val palette =
            Palette
                .create(
                    List(paletteCount) {
                        PixelColor.create(
                            ColorChannel.create(CHANNEL_MAX).requiredValue(),
                            ColorChannel.create(CHANNEL_MIN).requiredValue(),
                            ColorChannel.create(CHANNEL_MIN).requiredValue(),
                            ColorChannel.create(CHANNEL_MAX).requiredValue(),
                        )
                    },
                ).requiredValue()
        return EditorController.create(EditorRuntime.create(size, palette, FixedDocumentIdSource))
    }

    private fun <T> DomainValueResult<T>.requiredValue(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> error("Invalid layout fixture: $rejection")
        }

    private companion object {
        const val LAST_PALETTE_DESCRIPTION: String = "Palette color 32, RGBA 255, 0, 0, 255"
        const val ROOT_TAG: String = "fixed editor root"
        const val DOCUMENT_WIDTH: Int = 3
        const val DOCUMENT_HEIGHT: Int = 2
        const val CHANNEL_MIN: Int = 0
        const val CHANNEL_MAX: Int = 255
        const val CANVAS_DESCRIPTION: String = "3 by 2 pixel canvas"
        const val FIRST_PALETTE_DESCRIPTION: String = "Palette color 1, RGBA 255, 0, 0, 255"
    }
}

private object FixedDocumentIdSource : DocumentIdSource {
    override fun nextDocumentId(): DocumentId =
        when (val result = DocumentId.create("1".repeat(DOCUMENT_ID_LENGTH))) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid layout fixture document ID: ${result.rejection}")
        }

    private const val DOCUMENT_ID_LENGTH: Int = 32
}
