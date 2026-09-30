package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Draws [TransparencyBackdrop] alone into a Compose `Canvas` and checks the centre pixel of each
 * cell against [TransparencyBackdrop.colorAt] (ADR 0026, Issue #147), with the origin at the canvas
 * corner and with the origin moved.
 */
internal class TransparencyBackdropDrawTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cellCentresMatchColorAtWithOriginAtZero() {
        assertCellCentres(originX = 0, originY = 0)
    }

    @Test
    fun cellCentresMatchColorAtWithOriginMoved() {
        assertCellCentres(originX = ORIGIN_X, originY = ORIGIN_Y)
    }

    private fun assertCellCentres(
        originX: Int,
        originY: Int,
    ) {
        composeRule.setContent {
            val side = with(LocalDensity.current) { SIDE_PX.toDp() }
            val backdrop = remember { TransparencyBackdrop() }
            val area = remember { RectF() }
            Canvas(Modifier.requiredSize(side).testTag(TAG)) {
                area.set(originX.toFloat(), originY.toFloat(), size.width, size.height)
                drawIntoCanvas { backdrop.draw(it.nativeCanvas, area, CELL_PX) }
            }
        }
        val image = composeRule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        for (row in 0 until CELLS) {
            for (column in 0 until CELLS) {
                val x = column * CELL_PX + CELL_PX / 2
                val y = row * CELL_PX + CELL_PX / 2
                assertEquals(
                    "Cell ($column, $row) with origin ($originX, $originY)",
                    TransparencyBackdrop.colorAt(x, y, CELL_PX),
                    image[originX + x, originY + y].toArgb(),
                )
            }
        }
    }

    private companion object {
        const val TAG: String = "transparency_backdrop"
        const val SIDE_PX: Int = 64
        const val CELL_PX: Int = 10
        const val CELLS: Int = 4
        const val ORIGIN_X: Int = 7
        const val ORIGIN_Y: Int = 13
    }
}
