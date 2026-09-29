package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hideyukimori.nenepixel.core.application.document.command.AddLayerCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.canvas
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue #143 T5c: while a gesture runs, every canvas pixel shows exactly what committing that gesture
 * with [ApplyStrokeCommand] shows, on a three-layer document with translucent, opaque and Empty cells.
 */
@RunWith(AndroidJUnit4::class)
internal class StrokePreviewBitmapTest {
    @Test
    fun paintPreviewMatchesTheCommittedBitmapOnEveryLayer() {
        (0..2).forEach { target -> assertPreviewMatchesCommit(target, DrawingTool.Pencil, path) }
    }

    @Test
    fun erasePreviewMatchesTheCommittedBitmapOnEveryLayer() {
        (0..2).forEach { target -> assertPreviewMatchesCommit(target, DrawingTool.Eraser, path) }
    }

    @Test
    fun cancelledGestureLeavesNothingBehindForTheNextGesture() {
        val scene = scene()
        val committed = CommittedBitmapCache()
        val previews = PreviewBitmapCache()
        scene.begin(scene.layers[1], DrawingTool.Pencil, path)
        previews.render(scene.adapter.renderState, committed, BACKGROUND)
        scene.runtime.reduce(WorkspaceAction.CancelGesturePreview)
        assertNull(previews.render(scene.adapter.renderState, committed, BACKGROUND))

        scene.begin(scene.layers[0], DrawingTool.Eraser, shortPath)
        val preview = requireNotNull(previews.render(scene.adapter.renderState, committed, BACKGROUND)).pixels()
        scene.commit()

        assertArrayEquals("cancelled then erased", scene.committedPixels(), preview)
    }

    private fun assertPreviewMatchesCommit(
        target: Int,
        tool: DrawingTool,
        positions: List<PixelPosition>,
    ) {
        val scene = scene()
        val committed = CommittedBitmapCache()
        scene.begin(scene.layers[target], tool, positions)
        val preview =
            requireNotNull(PreviewBitmapCache().render(scene.adapter.renderState, committed, BACKGROUND)).pixels()
        scene.commit()

        assertArrayEquals("target=$target $tool", scene.committedPixels(), preview)
    }

    /** Bottom, middle and top layers with translucent, opaque and Empty cells, bottom active. */
    private fun scene(): Scene {
        val runtime = fixture(canvas(WIDTH, HEIGHT), palette).runtime
        val bottom = LayerId.first()
        runtime.execute(AddLayerCommand.create(runtime.captureSource(), bottom))
        val middle = layerIds(runtime)[1]
        runtime.execute(AddLayerCommand.create(runtime.captureSource(), middle))
        val scene = Scene(runtime, layerIds(runtime))
        scene.draw(scene.layers[0], DrawingTool.Pencil, 0, listOf(position(0, 0), position(3, 0)))
        scene.draw(scene.layers[0], DrawingTool.Pencil, 3, listOf(position(2, 1), position(3, 1)))
        scene.draw(scene.layers[1], DrawingTool.Pencil, 1, listOf(position(0, 0), position(1, 1)))
        scene.draw(scene.layers[1], DrawingTool.Pencil, 2, listOf(position(3, 0), position(3, 0)))
        scene.draw(scene.layers[2], DrawingTool.Pencil, 2, listOf(position(1, 0), position(2, 0)))
        scene.draw(scene.layers[2], DrawingTool.Pencil, 1, listOf(position(3, 1), position(3, 1)))
        return scene
    }

    private fun layerIds(runtime: EditorRuntime): List<LayerId> {
        val document = runtime.state.documentState
        return document.layers.map { it.id }
    }

    private class Scene(
        val runtime: EditorRuntime,
        val layers: List<LayerId>,
    ) {
        val adapter: EditorRuntimeAdapter = EditorRuntimeAdapter(runtime)

        fun draw(
            layer: LayerId,
            tool: DrawingTool,
            index: Int,
            positions: List<PixelPosition>,
        ) {
            runtime.reduce(WorkspaceAction.SelectPaletteEntry(PaletteIndex.create(index).requiredValue()))
            begin(layer, tool, positions)
            commit()
        }

        fun begin(
            layer: LayerId,
            tool: DrawingTool,
            positions: List<PixelPosition>,
        ) {
            runtime.reduce(WorkspaceAction.SelectLayer(layer))
            runtime.reduce(WorkspaceAction.SelectTool(tool))
            runtime.reduce(WorkspaceAction.BeginGesturePreview(runtime.state.documentState.size, positions.first()))
            positions.drop(1).forEach { runtime.reduce(WorkspaceAction.ExtendGesturePreview(it)) }
            checkNotNull(runtime.state.workspaceState.preview) { "The gesture on $layer did not start" }
        }

        fun commit() {
            val preparation = runtime.reduce(WorkspaceAction.PrepareGestureCommit)
            check(preparation is WorkspaceReductionResult.CommitPrepared) { "Commit was not prepared: $preparation" }
            val result =
                runtime.execute(
                    ApplyStrokeCommand.create(preparation.admission, preparation.layerId, preparation.stroke),
                )
            check(result is CommandResult.Applied) { "Stroke was not applied: $result" }
        }

        fun committedPixels(): IntArray {
            val document = runtime.state.documentState
            return CommittedBitmapCache().render(document, document.definition, BACKGROUND).pixels()
        }
    }

    private companion object {
        const val WIDTH: Int = 4
        const val HEIGHT: Int = 2
        const val BACKGROUND: Int = -0xefdfd0

        val palette: List<PixelColor> =
            listOf(0x204060ff, 0xa0c0e080.toInt(), 0xff000040.toInt(), 0x00ff00ff)
                .map(PixelColor::fromPackedRgba8888)

        /** Covers every pixel but (0, 1), with forward and backward segments. */
        val path: List<PixelPosition> = listOf(position(0, 0), position(3, 0), position(3, 1), position(1, 1))

        /** Touches only part of [path], so the cancelled gesture's other pixels must show the base again. */
        val shortPath: List<PixelPosition> = listOf(position(1, 0), position(2, 0))

        fun position(
            x: Int,
            y: Int,
        ): PixelPosition = PixelPosition.create(PixelX.create(x).requiredValue(), PixelY.create(y).requiredValue())
    }
}

private fun Bitmap.pixels(): IntArray {
    val actual = IntArray(width * height)
    getPixels(actual, 0, width, 0, 0, width, height)
    return actual
}

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Invalid stroke preview test fixture: $rejection")
    }
