package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class UndoRedoEditorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun strokeUndoRedoControlsRestoreExactSnapshotsAndRevisions() {
        val controller = controller()
        val initial = controller.renderState
        composeRule.setContent {
            NenePixelEditor(initialState = initial, callbacks = controller.callbacks)
        }

        composeRule.onNodeWithText("Undo").assertIsNotEnabled()
        composeRule.onNodeWithText("Redo").assertIsNotEnabled()
        composeRule
            .onNodeWithContentDescription("16 by 16 pixel canvas")
            .performTouchInput {
                swipe(
                    start = percentOffset(START_PERCENT, START_PERCENT),
                    end = percentOffset(END_PERCENT, END_PERCENT),
                    durationMillis = SWIPE_DURATION_MILLIS,
                )
            }
        composeRule.waitForIdle()

        val drawn = controller.renderState
        assertEquals(1L, drawn.snapshot.revision.value)
        assertTrue(drawn.canUndo)
        assertFalse(drawn.canRedo)
        composeRule.onNodeWithText("Undo").assertIsEnabled().performClick()
        composeRule.waitForIdle()

        val undone = controller.renderState
        assertEquals(initial.snapshot, undone.snapshot)
        assertEquals(0L, undone.snapshot.revision.value)
        assertFalse(undone.canUndo)
        assertTrue(undone.canRedo)
        composeRule.onNodeWithText("Redo").assertIsEnabled().performClick()
        composeRule.waitForIdle()

        val redone = controller.renderState
        assertEquals(drawn.snapshot, redone.snapshot)
        assertEquals(1L, redone.snapshot.revision.value)
        assertTrue(redone.canUndo)
        assertFalse(redone.canRedo)
    }

    @Test
    fun secondPointerCancelsDrawThenTransformsViewportWithoutDocumentHistory() {
        val controller = controller()
        val initial = controller.renderState
        composeRule.setContent {
            NenePixelEditor(initialState = initial, callbacks = controller.callbacks)
        }

        composeRule
            .onNodeWithContentDescription("16 by 16 pixel canvas")
            .performTouchInput {
                down(pointerId = 0, position = percentOffset(0.20f, 0.20f))
                moveTo(pointerId = 0, position = percentOffset(0.30f, 0.30f))
                down(pointerId = 1, position = percentOffset(0.80f, 0.80f))
                moveTo(pointerId = 0, position = percentOffset(0.15f, 0.15f))
                moveTo(pointerId = 1, position = percentOffset(0.85f, 0.85f))
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
            .onNodeWithContentDescription("16 by 16 pixel canvas")
            .performTouchInput {
                swipe(
                    start = percentOffset(START_PERCENT, START_PERCENT),
                    end = percentOffset(END_PERCENT, END_PERCENT),
                    durationMillis = SWIPE_DURATION_MILLIS,
                )
            }
        composeRule.waitForIdle()

        assertEquals(1L, controller.renderState.snapshot.revision.value)
    }

    @Test
    fun validNewDocumentCreatesCanonicalBlankRuntimeAndClosesDialog() {
        val ids = CountingDocumentIdSource()
        val controller = controller(ids)
        composeRule.setContent {
            NenePixelEditor(initialState = controller.renderState, callbacks = controller.callbacks)
        }

        openNewDocumentDialog()
        replaceDimensions(width = "3", height = "2")
        composeRule.onNodeWithText("Create").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Create new document").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("3 by 2 pixel canvas").assertExists()
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
            NenePixelEditor(initialState = controller.renderState, callbacks = controller.callbacks)
        }

        openNewDocumentDialog()
        replaceDimensions(width = "257", height = "2")
        composeRule.onNodeWithText("Create").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Width must be between 1 and 256.").assertExists()
        composeRule.onNodeWithText("Create new document").assertExists()
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
            NenePixelEditor(initialState = controller.renderState, callbacks = controller.callbacks)
        }

        openNewDocumentDialog()
        replaceDimensions(width = "64", height = "32")
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Create new document").assertDoesNotExist()
        assertEquals(1, ids.callCount)
        assertSame(beforeDocument, controller.documentState)
        assertSame(beforeWorkspace, controller.workspaceState)
    }

    private fun openNewDocumentDialog() {
        composeRule.onNodeWithText("New document").performClick()
        composeRule.onNodeWithText("Create new document").assertExists()
    }

    private fun replaceDimensions(
        width: String,
        height: String,
    ) {
        composeRule.onNodeWithContentDescription("Document width").performTextReplacement(width)
        composeRule.onNodeWithContentDescription("Document height").performTextReplacement(height)
    }

    private fun controller(documentIdSource: DocumentIdSource = CountingDocumentIdSource()): EditorController {
        val size =
            CanvasSize.create(
                CanvasWidth.create(CANVAS_EDGE).requiredValue(),
                CanvasHeight.create(CANVAS_EDGE).requiredValue(),
            )
        val activeColor = color(CHANNEL_MAX, CHANNEL_MIN, CHANNEL_MIN)
        return EditorController.create(
            EditorRuntime.create(size, activeColor, documentIdSource),
        )
    }

    private fun color(
        red: Int,
        green: Int,
        blue: Int,
    ): PixelColor =
        PixelColor.create(
            ColorChannel.create(red).requiredValue(),
            ColorChannel.create(green).requiredValue(),
            ColorChannel.create(blue).requiredValue(),
            ColorChannel.create(CHANNEL_MAX).requiredValue(),
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
        const val START_PERCENT: Float = 0.05f
        const val END_PERCENT: Float = 0.25f
        const val SWIPE_DURATION_MILLIS: Long = 300L
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
