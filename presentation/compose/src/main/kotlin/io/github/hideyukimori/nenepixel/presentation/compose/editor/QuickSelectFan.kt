package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelectItem
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/**
 * The open menu's items around the control and, while a slot is highlighted, its number above the control
 * (#108 UI spec "扇（fan）"). Items move out from the control centre when the menu opens; the fan leaves
 * composition, without animation, as soon as the menu closes.
 */
@Composable
internal fun QuickSelectFan(
    placement: QuickSelectFanPlacement,
    highlighted: QuickSelectItem?,
    palette: Palette,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(progress) { progress.animateTo(1f, tween(FAN_OPEN_MILLIS, easing = FastOutSlowInEasing)) }
    val itemSize = with(LocalDensity.current) { ITEM_SIZE.roundToPx() }
    Box(Modifier.fillMaxSize().testTag(QuickSelectSemantics.FAN_TAG), contentAlignment = AbsoluteAlignment.TopLeft) {
        placement.items.forEachIndexed { index, item ->
            QuickSelectFanItem(
                item,
                item == highlighted,
                palette,
                Modifier
                    .absoluteOffset { placement.itemTopLeft(index, itemSize, progress.value) }
                    .graphicsLayer { alpha = progress.value },
            )
        }
        (highlighted as? QuickSelectItem.PaletteSlot)?.let { slot ->
            QuickSelectSlotChip(QuickSelectSemantics.slotNumber(slot.index), placement)
        }
    }
}

@Composable
private fun QuickSelectFanItem(
    item: QuickSelectItem,
    highlighted: Boolean,
    palette: Palette,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val scale by animateFloatAsState(
        if (highlighted) HIGHLIGHT_SCALE else 1f,
        tween(HIGHLIGHT_MILLIS, easing = LinearOutSlowInEasing),
        label = "quick select highlight",
    )
    val label = QuickSelectSemantics.itemLabel(item)
    Box(
        modifier
            .size(ITEM_SIZE)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.editorDescription(
                label.resource,
                *label.arguments.toTypedArray(),
                identity = QuickSelectSemantics.itemTag(item),
            ).background(scheme.surface, CircleShape)
            .border(
                if (highlighted) HIGHLIGHT_RING else OUTLINE,
                if (highlighted) scheme.primary else scheme.outline,
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (item) {
            is QuickSelectItem.PaletteSlot -> {
                Box(Modifier.size(ITEM_SWATCH).background(palette.quickSelectColor(item.index), CircleShape))
            }

            QuickSelectItem.Eyedropper -> {
                CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
                    EditorSymbol(EditorIcon.Eyedropper)
                }
            }
        }
    }
}

/** The highlighted slot number, centred [CHIP_GAP] above the control. */
@Composable
private fun QuickSelectSlotChip(
    number: Int,
    placement: QuickSelectFanPlacement,
) {
    val scheme = MaterialTheme.colorScheme
    val gap = with(LocalDensity.current) { CHIP_GAP.roundToPx() }
    Text(
        number.toString(),
        modifier =
            Modifier
                .absoluteOffset { placement.aboveControl(gap) }
                .layout { measurable, _ ->
                    val chip = measurable.measure(Constraints())
                    layout(chip.width, chip.height) { chip.place(-chip.width / 2, -chip.height) }
                }.background(scheme.surface, RoundedCornerShape(CHIP_CORNER))
                .border(OUTLINE, scheme.outline, RoundedCornerShape(CHIP_CORNER))
                .padding(horizontal = CHIP_PADDING_HORIZONTAL, vertical = CHIP_PADDING_VERTICAL),
        color = scheme.onSurface,
        maxLines = 1,
        style = MaterialTheme.typography.labelMedium,
    )
}

/** The colour of a slot the menu offers; the reducer closes the menu before its palette can lose the slot. */
internal fun Palette.quickSelectColor(index: PaletteIndex): Color =
    when (val entry = entryAt(index)) {
        is DomainValueResult.Created -> entry.value.color.toComposeColor()
        is DomainValueResult.Rejected -> error("Quick-select slot is outside the palette: ${entry.rejection}")
    }

private const val FAN_OPEN_MILLIS: Int = 120
private const val HIGHLIGHT_MILLIS: Int = 80
private const val HIGHLIGHT_SCALE: Float = 1.15f
private val ITEM_SIZE = 40.dp
private val ITEM_SWATCH = 28.dp
private val OUTLINE = 1.dp
private val HIGHLIGHT_RING = 3.dp
private val CHIP_GAP = 8.dp
private val CHIP_CORNER = 4.dp
private val CHIP_PADDING_HORIZONTAL = 6.dp
private val CHIP_PADDING_VERTICAL = 2.dp
