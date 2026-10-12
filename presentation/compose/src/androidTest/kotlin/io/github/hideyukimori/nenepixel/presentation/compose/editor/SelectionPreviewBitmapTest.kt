package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Bitmap
import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.DocumentPoint
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelOrientation
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransferMode
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.canvas
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Direct shared-cache transitions in a library runtime; no app storage or user fixture. */
internal class SelectionPreviewBitmapTest {
    @Test
    fun zeroOffsetReturnsCommittedAndReusesPreparationWhenDisplacedAgain() {
        val scene = scene()
        scene.select()
        assertNull(scene.render())
        assertEquals(0, scene.previews.selectionPreparationCount)
        scene.shift(3)
        val first = requireNotNull(scene.render()).pixels()
        assertSame(scene.render(), scene.render())
        assertEquals(1, scene.previews.selectionPreparationCount)
        scene.shift(0)
        assertNull(scene.render())
        scene.shift(3)
        assertArrayEquals(first, requireNotNull(scene.render()).pixels())
        assertEquals(1, scene.previews.selectionPreparationCount)
        scene.runtime.reduce(WorkspaceAction.SetSelectionMode(PixelTransferMode.Copy))
        val copy = requireNotNull(scene.render())
        assertEquals(RED, copy.getPixel(0, 0))
        assertEquals(RED, copy.getPixel(3, 0))
        val shown = copy.pixels()
        scene.runtime.selectionOperations.applySelection()
        assertArrayEquals(shown, scene.committedPixels())
    }

    @Test
    fun strokeSelectionStrokeWithoutIntermediateFrameLeavesNoGhosts() {
        val scene = scene()
        scene.beginStroke(position(5, 1))
        scene.render()
        scene.runtime.reduce(WorkspaceAction.CancelGesturePreview)
        scene.select()
        scene.shift(3)
        scene.render()
        scene.runtime.reduce(WorkspaceAction.CancelSelectionSession)
        scene.beginStroke(position(2, 1))
        val shown = requireNotNull(scene.render()).pixels()
        scene.commitStroke()
        assertArrayEquals(scene.committedPixels(), shown)
        assertEquals(0, shown[5 + WIDTH])
        assertEquals(0, shown[3])
        assertEquals(0, shown[4])
    }

    @Test
    fun aNewSourceRectangleInvalidatesItsCropButOffsetAndModeDoNot() {
        val scene = scene()
        scene.select()
        scene.shift(3)
        scene.render()
        scene.runtime.reduce(WorkspaceAction.SetSelectionMode(PixelTransferMode.Copy))
        scene.render()
        assertEquals(1, scene.previews.selectionPreparationCount)
        scene.runtime.reduce(WorkspaceAction.BeginSelectionPointer(position(1, 0)))
        scene.runtime.reduce(WorkspaceAction.FinishSelectionPointer)
        assertNull(scene.render())
        assertEquals(
            "Changed zero source is forgotten without preparation",
            1,
            scene.previews.selectionPreparationCount,
        )
        scene.select()
        assertNull(scene.render())
        scene.shift(3)
        scene.render()
        assertEquals("Returning to the discarded source must prepare anew", 2, scene.previews.selectionPreparationCount)
        scene.runtime.reduce(WorkspaceAction.BeginSelectionPointer(position(1, 0)))
        scene.runtime.reduce(WorkspaceAction.FinishSelectionPointer)
        assertNull(scene.render())
        assertEquals(2, scene.previews.selectionPreparationCount)
        scene.shift(2)
        val shown = requireNotNull(scene.render())
        assertEquals(GREEN, shown.getPixel(2, 0))
        assertEquals(3, scene.previews.selectionPreparationCount)
    }

    @Test
    fun inPlaceQuarterTurnsReuseCropRestoreOldFootprintsAndEqualOneApply() {
        val scene = scene()
        scene.select()
        assertNull(scene.render())
        assertEquals(0, scene.previews.selectionPreparationCount)
        scene.runtime.reduce(WorkspaceAction.TransformSelection(PixelOrientation.RotateClockwise))
        val tall = requireNotNull(scene.render())
        assertEquals(RED, tall.getPixel(0, 0))
        assertEquals(GREEN, tall.getPixel(0, 1))
        assertEquals(0, tall.getPixel(1, 0))
        assertSame(tall, scene.render())
        assertEquals(1, scene.previews.selectionPreparationCount)
        scene.runtime.reduce(WorkspaceAction.TransformSelection(PixelOrientation.RotateClockwise))
        val wide = requireNotNull(scene.render())
        assertSame(tall, wide)
        assertEquals(GREEN, wide.getPixel(0, 0))
        assertEquals(RED, wide.getPixel(1, 0))
        assertEquals(0, wide.getPixel(0, 1))
        repeat(2) { scene.runtime.reduce(WorkspaceAction.TransformSelection(PixelOrientation.RotateClockwise)) }
        assertNull(scene.render())
        assertEquals(1, scene.previews.selectionPreparationCount)
        scene.runtime.reduce(WorkspaceAction.TransformSelection(PixelOrientation.FlipHorizontal))
        val flipped = requireNotNull(scene.render())
        assertEquals(GREEN, flipped.getPixel(0, 0))
        assertEquals(RED, flipped.getPixel(1, 0))
        assertEquals(0, flipped.getPixel(0, 1))
        scene.runtime.reduce(WorkspaceAction.SetSelectionMode(PixelTransferMode.Copy))
        val shown = requireNotNull(scene.render()).pixels()
        assertEquals(1, scene.previews.selectionPreparationCount)
        scene.runtime.selectionOperations.applySelection()
        assertArrayEquals(shown, scene.committedPixels())
    }

    private fun scene(): Scene {
        val runtime = fixture(canvas(WIDTH, HEIGHT)).runtime
        repeat(2) { index ->
            val stroke =
                Stroke
                    .create(
                        runtime.state.documentState.size,
                        listOf(position(index, 0)),
                        StrokeEffect.Paint(PaletteIndex.create(index).value()),
                    ).value()
            runtime.execute(ApplyStrokeCommand.create(runtime.captureSource(), LayerId.first(), stroke))
        }
        return Scene(runtime)
    }

    private class Scene(
        val runtime: EditorRuntime,
    ) {
        val previews = PreviewBitmapCache()
        private val committed = CommittedBitmapCache()
        private val adapter = EditorRuntimeAdapter(runtime)

        fun select() {
            runtime.selectionOperations.beginSelection(LayerId.first())
            runtime.reduce(WorkspaceAction.BeginSelectionPointer(position(0, 0)))
            runtime.reduce(WorkspaceAction.UpdateSelectionPointer(DocumentPoint.create(1, 0)))
            runtime.reduce(WorkspaceAction.FinishSelectionPointer)
        }

        fun shift(x: Int) {
            val origin =
                requireNotNull(
                    runtime.state.workspaceState.selectionSession
                        ?.targetRegion,
                ).origin
            runtime.reduce(WorkspaceAction.BeginSelectionPointer(origin))
            runtime.reduce(WorkspaceAction.UpdateSelectionPointer(DocumentPoint.create(x, 0)))
            runtime.reduce(WorkspaceAction.FinishSelectionPointer)
        }

        fun beginStroke(at: PixelPosition) {
            runtime.reduce(WorkspaceAction.SelectPaletteEntry(PaletteIndex.first))
            runtime.reduce(WorkspaceAction.SelectTool(DrawingTool.Pencil))
            runtime.reduce(WorkspaceAction.BeginGesturePreview(runtime.state.documentState.size, at))
        }

        fun commitStroke() {
            val result = runtime.reduce(WorkspaceAction.PrepareGestureCommit) as WorkspaceReductionResult.CommitPrepared
            runtime.execute(ApplyStrokeCommand.create(result.admission, result.layerId, result.stroke))
        }

        fun render(): Bitmap? = previews.render(adapter.renderState, committed)

        fun committedPixels(): IntArray {
            val document = runtime.state.documentState
            return committed.render(document, document.frames.single().id, document.definition, null).pixels()
        }
    }

    private companion object {
        const val WIDTH: Int = 6
        const val HEIGHT: Int = 2
        const val RED: Int = 0xFFFF0000.toInt()
        const val GREEN: Int = 0xFF00FF00.toInt()

        fun position(
            x: Int,
            y: Int,
        ): PixelPosition = PixelPosition.create(PixelX.create(x).value(), PixelY.create(y).value())

        fun <T> DomainValueResult<T>.value(): T =
            when (this) {
                is DomainValueResult.Created -> value
                is DomainValueResult.Rejected -> error("Invalid fixture: $rejection")
            }

        fun Bitmap.pixels(): IntArray = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
    }
}
