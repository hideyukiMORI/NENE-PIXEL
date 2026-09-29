package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt

/**
 * Display-only checkerboard drawn under the picture so transparent pixels are visible (ADR 0026,
 * Issue #147). One 2x2-cell bitmap is repeated by a [BitmapShader] and one rectangle is filled per
 * draw. The bitmap, shader, [Paint] and [Matrix] are kept until the cell size in pixels changes, so
 * a frame allocates nothing. The shader's local matrix is set again only when the origin moves, so
 * a stroke on a still view leaves the shader untouched.
 */
internal class TransparencyBackdrop {
    private val paint: Paint =
        Paint().apply {
            isAntiAlias = false
            isDither = false
            isFilterBitmap = false
        }
    private val localMatrix: Matrix = Matrix()
    private var shaderCellPx: Int = 0
    private var originLeft: Float = Float.NaN
    private var originTop: Float = Float.NaN

    /**
     * Fills [area] once with the checkerboard whose top-left cell (light) starts at the top-left
     * corner of [area]. [cellPx] comes from [cellPx] of the current density.
     */
    fun draw(
        canvas: Canvas,
        area: RectF,
        cellPx: Int,
    ) {
        if (shaderCellPx != cellPx) {
            paint.shader = BitmapShader(tile(cellPx), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            shaderCellPx = cellPx
            originLeft = Float.NaN
        }
        if (originLeft != area.left || originTop != area.top) {
            originLeft = area.left
            originTop = area.top
            localMatrix.setTranslate(originLeft, originTop)
            paint.shader.setLocalMatrix(localMatrix)
        }
        canvas.drawRect(area, paint)
    }

    private fun tile(cellPx: Int): Bitmap {
        val edge = cellPx * CELLS_PER_TILE
        val argb = IntArray(edge * edge) { index -> colorAt(index % edge, index / edge, cellPx) }
        return Bitmap.createBitmap(argb, edge, edge, Bitmap.Config.ARGB_8888)
    }

    companion object {
        private const val CELLS_PER_TILE: Int = 2
        private const val MIN_CELL_PX: Int = 2
        private val LIGHT_ARGB: Int = PresentationPalette.transparencyLight.toArgb()
        private val DARK_ARGB: Int = PresentationPalette.transparencyDark.toArgb()

        /** Cell edge in whole pixels: [PresentationPalette.transparencyCell] times [density], rounded, at least 2. */
        fun cellPx(density: Float): Int =
            (PresentationPalette.transparencyCell.value * density).roundToInt().coerceAtLeast(MIN_CELL_PX)

        /**
         * ARGB of the backdrop at ([x], [y]) pixels from its origin, with the same rule as the shader:
         * the top-left cell is [PresentationPalette.transparencyLight]. [x] and [y] are never negative;
         * callers only ask for points inside the backdrop rectangle.
         */
        fun colorAt(
            x: Int,
            y: Int,
            cellPx: Int,
        ): Int = if ((x / cellPx + y / cellPx) % CELLS_PER_TILE == 0) LIGHT_ARGB else DARK_ARGB
    }
}
