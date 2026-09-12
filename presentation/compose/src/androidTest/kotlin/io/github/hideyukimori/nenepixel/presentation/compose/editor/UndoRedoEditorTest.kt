package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class UndoRedoEditorTest {
    private lateinit var activeController: EditorController

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun strokeUndoRedoControlsRestoreExactSnapshotsAndRevisions() {
        val controller = controller()
        val initial = controller.renderState
        composeRule.setContent {
            TestNenePixelEditor(controller)
        }

        composeRule.onNodeWithTag("editor_undo").assertIsNotEnabled()
        composeRule.onNodeWithTag("editor_redo").assertIsNotEnabled()
        composeRule.onNodeWithTag("editor_clean_document").assertExists()
        composeRule
            .onNodeWithTag("editor_canvas_16_16")
            .performTouchInput {
                swipe(
                    start = documentOffset(START_PERCENT, START_PERCENT),
                    end = documentOffset(END_PERCENT, END_PERCENT),
                    durationMillis = SWIPE_DURATION_MILLIS,
                )
            }
        composeRule.waitForIdle()

        val drawn = controller.renderState
        assertEquals(1L, drawn.snapshot.revision.value)
        assertTrue(drawn.canUndo)
        assertFalse(drawn.canRedo)
        composeRule.onNodeWithTag("editor_dirty_document").assertExists()
        composeRule.onNodeWithTag("editor_undo").assertIsEnabled().performClick()
        composeRule.waitForIdle()

        val undone = controller.renderState
        assertEquals(initial.snapshot, undone.snapshot)
        assertEquals(0L, undone.snapshot.revision.value)
        assertFalse(undone.canUndo)
        assertTrue(undone.canRedo)
        composeRule.onNodeWithTag("editor_clean_document").assertExists()
        composeRule.onNodeWithTag("editor_redo").assertIsEnabled().performClick()
        composeRule.waitForIdle()

        val redone = controller.renderState
        assertEquals(drawn.snapshot, redone.snapshot)
        assertEquals(1L, redone.snapshot.revision.value)
        assertTrue(redone.canUndo)
        assertFalse(redone.canRedo)
        composeRule.onNodeWithTag("editor_dirty_document").assertExists()
    }

    @Test
    fun multiStepHistoryExposesBothControlsAndNewBranchClearsRedo() {
        val controller = controller()
        composeRule.setContent {
            TestNenePixelEditor(controller)
        }

        touchPixel(FIRST_PIXEL_PERCENT)
        selectSecondColor()
        touchPixel(SECOND_PIXEL_PERCENT)
        composeRule.waitForIdle()

        assertEquals(2L, controller.renderState.snapshot.revision.value)
        composeRule.onNodeWithTag("editor_undo").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("editor_undo").assertIsEnabled()
        composeRule.onNodeWithTag("editor_redo").assertIsEnabled()
        composeRule.onNodeWithTag("editor_dirty_document").assertExists()

        composeRule.onNodeWithTag("editor_undo").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("editor_undo").assertIsNotEnabled()
        composeRule.onNodeWithTag("editor_redo").assertIsEnabled()
        composeRule.onNodeWithTag("editor_clean_document").assertExists()

        touchPixel(THIRD_PIXEL_PERCENT)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("editor_undo").assertIsEnabled()
        composeRule.onNodeWithTag("editor_redo").assertIsNotEnabled()
        composeRule.onNodeWithTag("editor_dirty_document").assertExists()
    }

    @Test
    fun secondPointerCancelsDrawThenTransformsViewportWithoutDocumentHistory() {
        val controller = controller()
        val initial = controller.renderState
        composeRule.setContent {
            TestNenePixelEditor(controller)
        }

        composeRule
            .onNodeWithTag("editor_canvas_16_16")
            .performTouchInput {
                down(pointerId = 0, position = documentOffset(0.20f, 0.20f))
                moveTo(pointerId = 0, position = documentOffset(0.30f, 0.30f))
                down(pointerId = 1, position = documentOffset(0.80f, 0.80f))
                moveTo(pointerId = 0, position = documentOffset(0.15f, 0.15f))
                moveTo(pointerId = 1, position = documentOffset(0.85f, 0.85f))
                up(pointerId = 0)
                up(pointerId = 1)
            }
        composeRule.waitForIdle()

        val transformed = controller.renderState
        assertNotEquals(initial.viewport, transformed.viewport)
        assertEquals(initial.snapshot, transformed.snapshot)
        assertEquals(0L, transformed.snapshot.revision.value)
        assertFalse(transformed.canUndo)
        assertFalse(transformed.canRedo)

        composeRule
            .onNodeWithTag("editor_canvas_16_16")
            .performTouchInput {
                swipe(
                    start = documentOffset(AFTER_ZOOM_START_PERCENT, AFTER_ZOOM_START_PERCENT),
                    end = documentOffset(AFTER_ZOOM_END_PERCENT, AFTER_ZOOM_END_PERCENT),
                    durationMillis = SWIPE_DURATION_MILLIS,
                )
            }
        composeRule.waitForIdle()

        assertEquals(1L, controller.renderState.snapshot.revision.value)
    }

    @Test
    fun pencilAndEraserControlsUseOneWorkspaceSelectionAndCommandPath() {
        val controller = controller()
        composeRule.setContent {
            TestNenePixelEditor(controller)
        }

        composeRule.onNodeWithTag("editor_pencil_tool").assertIsSelected()
        composeRule.onNodeWithTag("editor_eraser_tool").assertIsNotSelected()
        touchFirstPixel()
        composeRule.waitForIdle()

        val drawn = controller.renderState
        assertEquals(1L, drawn.snapshot.revision.value)
        assertTrue(drawn.snapshot.copyPackedRgba8888().any { pixel -> pixel != PixelColor.blank.toPackedRgba8888() })

        composeRule.onNodeWithTag("editor_eraser_tool").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("editor_eraser_tool").assertIsSelected()
        composeRule.onNodeWithTag("editor_pencil_tool").assertIsNotSelected()
        assertEquals(drawn.snapshot, controller.renderState.snapshot)

        touchFirstPixel()
        composeRule.waitForIdle()

        assertEquals(2L, controller.renderState.snapshot.revision.value)
        assertTrue(
            controller.renderState.snapshot
                .copyPackedRgba8888()
                .all { pixel -> pixel == PixelColor.blank.toPackedRgba8888() },
        )
    }

    @Test
    fun alreadyBlankEraserGestureIsNoOpWithoutHistory() {
        val controller = controller()
        composeRule.setContent {
            TestNenePixelEditor(controller)
        }

        composeRule.onNodeWithTag("editor_eraser_tool").performClick()
        touchFirstPixel()
        composeRule.waitForIdle()

        assertEquals(0L, controller.renderState.snapshot.revision.value)
        assertFalse(controller.renderState.canUndo)
        assertFalse(controller.renderState.canRedo)
        composeRule.onNodeWithTag("editor_undo").assertIsNotEnabled()
    }

    @Test
    fun paletteControlsExposeSelectionAndCommitExactRgbaThroughTheCanonicalPath() {
        val controller = controller()
        composeRule.setContent {
            TestNenePixelEditor(controller)
        }

        composeRule.onNodeWithTag("editor_open_palette").performClick()
        composeRule.onNodeWithTag(FIRST_PALETTE_DESCRIPTION).assertIsSelected()
        composeRule.onNodeWithTag(SECOND_PALETTE_DESCRIPTION).assertIsNotSelected()
        val before = controller.renderState.snapshot

        selectSecondColor()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("editor_open_palette").performClick()
        composeRule.onNodeWithTag(FIRST_PALETTE_DESCRIPTION).assertIsNotSelected()
        composeRule.onNodeWithTag(SECOND_PALETTE_DESCRIPTION).assertIsSelected()
        composeRule.onNodeWithTag("editor_close_panel").performClick()
        assertEquals(EXACT_PALETTE_RGBA, controller.renderState.activeColor.toPackedRgba8888())
        assertSame(before, controller.renderState.snapshot)
        assertFalse(controller.renderState.canUndo)
        assertFalse(controller.renderState.canRedo)

        touchFirstPixel()
        composeRule.waitForIdle()

        assertEquals(
            EXACT_PALETTE_RGBA,
            controller.renderState.snapshot
                .copyPackedRgba8888()
                .first(),
        )
        assertEquals(1L, controller.renderState.snapshot.revision.value)
    }

    @Test
    fun validNewDocumentCreatesCanonicalBlankRuntimeAndClosesDialog() {
        val ids = CountingDocumentIdSource()
        val controller = controller(ids)
        composeRule.setContent {
            TestNenePixelEditor(controller)
        }

        composeRule.onNodeWithTag("editor_eraser_tool").performClick()
        selectSecondColor()
        openNewDocumentDialog()
        replaceDimensions(width = "3", height = "2")
        composeRule.onNodeWithTag("editor_create").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("editor_create_document_title").assertDoesNotExist()
        composeRule.onNodeWithTag("editor_canvas_3_2").assertExists()
        composeRule.onNodeWithTag("editor_pencil_tool").assertIsSelected()
        composeRule.onNodeWithTag("editor_open_palette").performClick()
        composeRule.onNodeWithTag(FIRST_PALETTE_DESCRIPTION).assertIsSelected()
        assertEquals(2, ids.callCount)
        assertEquals(3, controller.renderState.snapshot.size.width.value)
        assertEquals(2, controller.renderState.snapshot.size.height.value)
        assertEquals(0L, controller.renderState.snapshot.revision.value)
        assertFalse(controller.renderState.canUndo)
        assertFalse(controller.renderState.canRedo)
    }

    @Test
    fun invalidNewDocumentShowsCorrectionAndPreservesCurrentRuntime() {
        val ids = CountingDocumentIdSource()
        val controller = controller(ids)
        val beforeDocument = controller.documentState
        val beforeWorkspace = controller.workspaceState
        composeRule.setContent {
            TestNenePixelEditor(controller)
        }

        openNewDocumentDialog()
        replaceDimensions(width = "257", height = "2")
        composeRule.onNodeWithTag("editor_create").performClick()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag(
                "editor_dimension_rejection",
            ).assertTextEquals(
                InstrumentationRegistry.getInstrumentation().targetContext.resources.let { resources ->
                    resources.getString(R.string.range_dimension, resources.getString(R.string.width), 1, 256)
                },
            ).assertExists()
        composeRule.onNodeWithTag("editor_create_document_title").assertExists()
        assertEquals(1, ids.callCount)
        assertSame(beforeDocument, controller.documentState)
        assertSame(beforeWorkspace, controller.workspaceState)
    }

    @Test
    fun cancelledNewDocumentAllocatesNothingAndPreservesCurrentRuntime() {
        val ids = CountingDocumentIdSource()
        val controller = controller(ids)
        val beforeDocument = controller.documentState
        val beforeWorkspace = controller.workspaceState
        composeRule.setContent {
            TestNenePixelEditor(controller)
        }

        openNewDocumentDialog()
        replaceDimensions(width = "64", height = "32")
        composeRule.onNodeWithTag("editor_cancel").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("editor_create_document_title").assertDoesNotExist()
        assertEquals(1, ids.callCount)
        assertSame(beforeDocument, controller.documentState)
        assertSame(beforeWorkspace, controller.workspaceState)
    }

    private fun selectSecondColor() {
        if (composeRule.onAllNodesWithContentDescription("editor_close_panel").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag("editor_open_palette").performClick()
        }
        composeRule.onNodeWithTag(SECOND_PALETTE_DESCRIPTION).performClick()
    }

    private fun TouchInjectionScope.documentOffset(
        x: Float,
        y: Float,
    ): Offset {
        val current = activeController.renderState
        val surface =
            when (val result = ViewportSurface.create(width, height, composeRule.density.density.toDouble())) {
                is ViewportValueResult.Created -> result.value
                is ViewportValueResult.Rejected -> error("Invalid test surface: ${result.rejection}")
            }
        val transform = checkNotNull(createViewportTransform(current.snapshot.size, surface, current.viewport))
        val pixel =
            pixelPosition(
                (x * current.snapshot.size.width.value).toInt(),
                (
                    y *
                        current.snapshot.size.height.value
                ).toInt(),
            )
        val bounds = checkNotNull(transform.surfaceBounds(pixel))
        return Offset(((bounds.left + bounds.right) / 2.0).toFloat(), ((bounds.top + bounds.bottom) / 2.0).toFloat())
    }

    private fun openNewDocumentDialog() {
        composeRule.onNodeWithTag("editor_file").performClick()
        composeRule.waitUntil {
            composeRule
                .onAllNodes(hasTestTag("editor_new_document") and isEnabled())
                .fetchSemanticsNodes()
                .size == 1
        }
        composeRule.onNodeWithTag("editor_new_document").performClick()
        composeRule.onNodeWithTag("editor_create_document_title").assertExists()
    }

    private fun touchFirstPixel() {
        touchPixel(FIRST_PIXEL_PERCENT)
    }

    private fun touchPixel(percent: Float) {
        composeRule
            .onNodeWithTag("editor_canvas_16_16")
            .performTouchInput {
                down(position = documentOffset(percent, FIRST_PIXEL_PERCENT))
                up()
            }
    }

    private fun replaceDimensions(
        width: String,
        height: String,
    ) {
        composeRule.onNodeWithTag("editor_document_width").performTextReplacement(width)
        composeRule.onNodeWithTag("editor_document_height").performTextReplacement(height)
    }

    private fun controller(documentIdSource: DocumentIdSource = CountingDocumentIdSource()): EditorController {
        val size =
            CanvasSize.create(
                CanvasWidth.create(CANVAS_EDGE).requiredValue(),
                CanvasHeight.create(CANVAS_EDGE).requiredValue(),
            )
        val palette =
            Palette
                .create(
                    listOf(
                        color(CHANNEL_MAX, CHANNEL_MIN, CHANNEL_MIN),
                        color(1, 2, 3, alpha = 4),
                    ),
                ).requiredValue()
        return EditorController
            .create(
                EditorRuntime.create(size, palette, documentIdSource),
            ).also { activeController = it }
    }

    private fun color(
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int = CHANNEL_MAX,
    ): PixelColor =
        PixelColor.create(
            ColorChannel.create(red).requiredValue(),
            ColorChannel.create(green).requiredValue(),
            ColorChannel.create(blue).requiredValue(),
            ColorChannel.create(alpha).requiredValue(),
        )

    private fun <T> DomainValueResult<T>.requiredValue(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> error("Invalid UI test fixture: $rejection")
        }

    private companion object {
        const val CANVAS_EDGE: Int = 16
        const val CHANNEL_MIN: Int = 0
        const val CHANNEL_MAX: Int = 255
        const val AFTER_ZOOM_START_PERCENT: Float = 0.45f
        const val AFTER_ZOOM_END_PERCENT: Float = 0.55f
        const val START_PERCENT: Float = 0.05f
        const val END_PERCENT: Float = 0.25f
        const val FIRST_PIXEL_PERCENT: Float = 0.03f
        const val SECOND_PIXEL_PERCENT: Float = 0.09f
        const val THIRD_PIXEL_PERCENT: Float = 0.15f
        const val SWIPE_DURATION_MILLIS: Long = 300L
        const val EXACT_PALETTE_RGBA: Int = 0x01020304
        const val FIRST_PALETTE_DESCRIPTION: String = "editor_palette_entry_1"
        const val SECOND_PALETTE_DESCRIPTION: String = "editor_palette_entry_2"
    }
}

private class CountingDocumentIdSource : DocumentIdSource {
    var callCount: Int = 0
        private set

    override fun nextDocumentId(): DocumentId {
        callCount += 1
        val value = callCount.coerceAtMost(9).toString().repeat(DOCUMENT_ID_LENGTH)
        return when (val result = DocumentId.create(value)) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid UI test document ID: ${result.rejection}")
        }
    }

    private companion object {
        const val DOCUMENT_ID_LENGTH: Int = 32
    }
}
