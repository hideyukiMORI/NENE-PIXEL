package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind

/** The shared artwork transparency backdrop also reveals partial/zero alpha in palette swatches. */
@Composable
internal fun Modifier.paletteCheckerboard(): Modifier {
    val backdrop = remember { TransparencyBackdrop() }
    val area = remember { RectF() }
    return drawBehind {
        area.set(0f, 0f, size.width, size.height)
        drawTransparencyBackdrop(backdrop, area)
    }
}
