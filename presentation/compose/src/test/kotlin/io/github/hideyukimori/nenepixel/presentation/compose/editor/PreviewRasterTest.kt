package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.ToolGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceState
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.presentation.compose.EditorFixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.canvas
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PreviewRasterTest {
    private val editor: EditorFixture = fixture(canvas(WIDTH, HEIGHT))

    @Test
    fun `two sample diagonal paints every interpolated position and nothing else`() {
        val gesture = gesture(listOf(position(0, 0), position(5, 2)))
        val raster = PreviewRaster(WIDTH, HEIGHT)

        raster.paint(gesture, ARGB)

        val expected = positionsOf(gesture)
        assertTrue(expected.size > 2, "Two samples must interpolate intermediate positions")
        assertRaster(raster, expected, ARGB)
    }

    @Test
    fun `second paint clears the previous preview range`() {
        val first = gesture(listOf(position(0, 0), position(5, 2)))
        val second = gesture(listOf(position(1, 3), position(4, 3)))
        val raster = PreviewRaster(WIDTH, HEIGHT)
        raster.paint(first, ARGB)

        raster.paint(second, OTHER_ARGB)

        assertRaster(raster, positionsOf(second), OTHER_ARGB)
    }

    @Test
    fun `revisiting a position keeps the exact colour without accumulating alpha`() {
        val gesture = gesture(listOf(position(0, 1), position(4, 1), position(0, 1), position(4, 1)))
        val raster = PreviewRaster(WIDTH, HEIGHT)

        raster.paint(gesture, ARGB)
        raster.paint(gesture, ARGB)

        assertTrue(gesture.positionCount > positionsOf(gesture).size, "The gesture must revisit positions")
        assertRaster(raster, positionsOf(gesture), ARGB)
    }

    @Test
    fun `clear resets painted positions and reports only the rows it touched`() {
        val gesture = gesture(listOf(position(1, 1), position(3, 2)))
        val raster = PreviewRaster(WIDTH, HEIGHT)
        raster.paint(gesture, ARGB)
        raster.transferChangedRows { _, _ -> }

        raster.clear()

        var changed: IntRange? = null
        raster.transferChangedRows { _, rows -> changed = rows }
        assertEquals(1..2, changed)
        assertRaster(raster, emptySet(), ARGB)
    }

    private fun gesture(samples: List<PixelPosition>): ToolGesture {
        var workspace: WorkspaceState =
            reduce(
                editor.initialWorkspace,
                WorkspaceAction.BeginGesturePreview(editor.initialDocument.size, samples.first()),
            )
        samples.drop(1).forEach { sample ->
            workspace = reduce(workspace, WorkspaceAction.ExtendGesturePreview(sample))
        }
        return requireNotNull(workspace.preview)
    }

    private fun reduce(
        workspace: WorkspaceState,
        action: WorkspaceAction,
    ): WorkspaceState = editor.reducer.reduce(workspace, action, editor.runtime.captureSource()).nextState

    private fun positionsOf(gesture: ToolGesture): Set<Pair<Int, Int>> =
        buildSet { gesture.forEachPosition { add(it.x.value to it.y.value) } }

    private fun assertRaster(
        raster: PreviewRaster,
        painted: Set<Pair<Int, Int>>,
        argb: Int,
    ) {
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                val expected = if ((x to y) in painted) argb else 0
                assertEquals(expected, raster.pixels[y * WIDTH + x], "pixel ($x, $y)")
            }
        }
    }

    private companion object {
        const val WIDTH: Int = 6
        const val HEIGHT: Int = 4
        const val ARGB: Int = 0x8CFF0000.toInt()
        const val OTHER_ARGB: Int = 0x8C00FF00.toInt()
    }
}
