package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyImportProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyPreviewColorResult
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.R

@Composable
internal fun LegacyPreviewComparison(source: LegacyImportProjection) {
    val original = remember(source.source) { previewBitmap(source.source.size, source.source::colorAt) }
    val reduction = source.reduction
    val converted = remember(reduction?.handle) { reduction?.let { previewBitmap(it.size, it::colorAt) } }
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) { LegacyPreviewImage(original, R.string.legacy_source) }
        Column(Modifier.weight(1f)) { LegacyPreviewImage(converted, R.string.legacy_result) }
    }
}

@Composable
private fun LegacyPreviewImage(
    bitmap: Bitmap?,
    label: Int,
) {
    Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
    Box(Modifier.fillMaxWidth().height(160.dp).clipToBounds(), contentAlignment = Alignment.Center) {
        LegacyTransparencyGrid()
        if (bitmap == null) {
            Text(
                stringResource(R.string.legacy_preview_empty),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp),
            )
        } else {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = stringResource(label),
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
            )
        }
    }
}

@Composable
private fun LegacyTransparencyGrid() {
    val light = MaterialTheme.colorScheme.surfaceContainer
    val dark = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(Modifier.fillMaxSize()) {
        drawRect(light)
        val edge = 8.dp.toPx()
        val columns = (size.width / edge).toInt() + 1
        val rows = (size.height / edge).toInt() + 1
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                if ((row + column) % 2 == 0) {
                    drawRect(dark, Offset(column * edge, row * edge), Size(edge, edge))
                }
            }
        }
    }
}

@Composable
internal fun LegacyPaletteSwatches(definition: PaletteDefinition) {
    val colors = remember(definition) { definition.palette.entries().map { it.color.toComposeColor() } }
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        val width = size.width / colors.size
        colors.forEachIndexed { index, color ->
            drawRect(color, Offset(index * width, 0f), Size(width, size.height))
        }
    }
}

private fun previewBitmap(
    canvas: CanvasSize,
    colorAt: (PixelPosition) -> LegacyPreviewColorResult,
): Bitmap {
    val width = canvas.width.value
    val colors =
        IntArray(width * canvas.height.value) { index ->
            val position =
                PixelPosition.create(
                    PixelX.create(index % width).requiredPreviewValue(),
                    PixelY.create(index / width).requiredPreviewValue(),
                )
            when (val color = colorAt(position)) {
                is LegacyPreviewColorResult.Color -> {
                    val rgba = color.value.toPackedRgba8888()
                    ((rgba and CHANNEL_MASK) shl ALPHA_SHIFT) or (rgba ushr CHANNEL_SHIFT)
                }

                LegacyPreviewColorResult.OutsideCanvas -> {
                    error("Preview iteration escaped its canvas")
                }
            }
        }
    return Bitmap.createBitmap(colors, width, canvas.height.value, Bitmap.Config.ARGB_8888)
}

private fun <T> DomainValueResult<T>.requiredPreviewValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Invalid preview coordinate: $rejection")
    }

private const val CHANNEL_MASK = 0xff
private const val ALPHA_SHIFT = 24
private const val CHANNEL_SHIFT = 8
