package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.ToolGesture

/**
 * Canvas-sized ARGB raster of the committed picture with the running gesture's positions replaced
 * (Issues #124, #143). It is derived, disposable workspace rendering state, never a document owner.
 * Painting replaces the previous preview and only the rows touched by the previous or current
 * preview, or by a base replacement, are reported as changed.
 */
internal class PreviewRaster(
    val width: Int,
    val height: Int,
) {
    private val base: IntArray = IntArray(width * height)
    private val buffer: IntArray = IntArray(width * height)

    /** Read-only view of the raster in row-major order. */
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

    /**
     * Lets [source] overwrite the committed picture (row-major, stride [width]); the raster then shows
     * the new base everywhere and reports every row as changed.
     */
    fun replaceBase(source: (base: IntArray) -> Unit) {
        source(base)
        base.copyInto(buffer)
        forgetPainted()
        markChangedRows(0, height - 1)
    }

    /** Restores only the bounding box painted since the previous clear to the base values. */
    fun clear() {
        if (paintedTop > paintedBottom) return
        val span = paintedRight - paintedLeft + 1
        for (y in paintedTop..paintedBottom) {
            val start = y * width + paintedLeft
            System.arraycopy(base, start, buffer, start, span)
        }
        markChangedRows(paintedTop, paintedBottom)
        forgetPainted()
    }

    /** Replaces the previous preview with every position of [gesture] written in its colour from [colors]. */
    fun paint(
        gesture: ToolGesture,
        colors: PreviewColorSource,
    ) {
        clear()
        gesture.forEachPosition { position ->
            val x = position.x.value
            val y = position.y.value
            check(x in 0 until width && y in 0 until height) {
                "Preview position ($x, $y) is outside the ${width}x$height raster"
            }
            buffer[y * width + x] = colors.argbAt(position)
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

    private fun forgetPainted() {
        paintedLeft = EMPTY_START
        paintedTop = EMPTY_START
        paintedRight = EMPTY_END
        paintedBottom = EMPTY_END
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
