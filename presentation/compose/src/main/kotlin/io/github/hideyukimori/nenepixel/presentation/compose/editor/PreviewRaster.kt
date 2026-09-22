package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.ToolGesture

/**
 * Canvas-sized ARGB raster of the running gesture preview (Issue #124). It is derived, disposable
 * workspace rendering state, never a document owner. Painting replaces the previous preview and
 * only the rows touched by the previous or current preview are reported as changed.
 */
internal class PreviewRaster(
    val width: Int,
    val height: Int,
) {
    private val buffer: IntArray = IntArray(width * height)

    /** Read-only view of the raster in row-major order; `0` is transparent. */
    val pixels: List<Int> = buffer.asList()

    private var paintedLeft: Int = EMPTY_START
    private var paintedTop: Int = EMPTY_START
    private var paintedRight: Int = EMPTY_END
    private var paintedBottom: Int = EMPTY_END
    private var changedTop: Int = EMPTY_START
    private var changedBottom: Int = EMPTY_END

    init {
        require(width > 0 && height > 0) { "Preview raster requires a positive size: ${width}x$height" }
    }

    /** Resets only the bounding box painted since the previous clear. */
    fun clear() {
        if (paintedTop > paintedBottom) return
        for (y in paintedTop..paintedBottom) {
            buffer.fill(0, y * width + paintedLeft, y * width + paintedRight + 1)
        }
        markChangedRows(paintedTop, paintedBottom)
        paintedLeft = EMPTY_START
        paintedTop = EMPTY_START
        paintedRight = EMPTY_END
        paintedBottom = EMPTY_END
    }

    /** Replaces the previous preview with every position of [gesture] written as [argb]. */
    fun paint(
        gesture: ToolGesture,
        argb: Int,
    ) {
        clear()
        gesture.forEachPosition { position ->
            val x = position.x.value
            val y = position.y.value
            check(x in 0 until width && y in 0 until height) {
                "Preview position ($x, $y) is outside the ${width}x$height raster"
            }
            buffer[y * width + x] = argb
            paintedLeft = minOf(paintedLeft, x)
            paintedTop = minOf(paintedTop, y)
            paintedRight = maxOf(paintedRight, x)
            paintedBottom = maxOf(paintedBottom, y)
        }
        markChangedRows(paintedTop, paintedBottom)
    }

    /**
     * Hands the backing rows changed since the previous transfer to [target] (row-major, stride
     * [width]) and forgets them. Nothing is handed over when no row changed.
     */
    fun transferChangedRows(target: (pixels: IntArray, rows: IntRange) -> Unit) {
        if (changedTop > changedBottom) return
        val rows = changedTop..changedBottom
        changedTop = EMPTY_START
        changedBottom = EMPTY_END
        target(buffer, rows)
    }

    private fun markChangedRows(
        top: Int,
        bottom: Int,
    ) {
        if (top > bottom) return
        changedTop = minOf(changedTop, top)
        changedBottom = maxOf(changedBottom, bottom)
    }

    private companion object {
        const val EMPTY_START: Int = Int.MAX_VALUE
        const val EMPTY_END: Int = Int.MIN_VALUE
    }
}
