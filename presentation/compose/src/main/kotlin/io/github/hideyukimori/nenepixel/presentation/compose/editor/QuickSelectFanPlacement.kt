package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelectItem
import kotlin.math.roundToInt

/**
 * The open menu's items laid out around the control: [centers] are control-local item centres in [items] order.
 * The reducer only ever receives the item under the pointer, never a position (CMD-010).
 */
internal data class QuickSelectFanPlacement(
    val items: List<QuickSelectItem>,
    val centers: List<QuickSelectFanPoint>,
    val geometry: QuickSelectGeometry,
) {
    /** The item under the control-local [pointer], or null on the control or away from every item. */
    fun itemAt(pointer: Offset): QuickSelectItem? =
        geometry.layout
            .hitTest(QuickSelectFanPoint(pointer.x, pointer.y), centers, geometry.controlCenter)
            ?.let(items::get)

    /**
     * The work-area top-left of an item [itemSize] pixels wide at [index], [progress] of the way from the control
     * centre to its place in the fan.
     */
    fun itemTopLeft(
        index: Int,
        itemSize: Int,
        progress: Float,
    ): IntOffset {
        val control = geometry.controlCenter
        val center = centers[index]
        return IntOffset(
            (geometry.origin.x + control.x + (center.x - control.x) * progress - itemSize / 2f).roundToInt(),
            (geometry.origin.y + control.y + (center.y - control.y) * progress - itemSize / 2f).roundToInt(),
        )
    }

    /** The work-area point [gap] pixels above the control's top edge, horizontally at its centre. */
    fun aboveControl(gap: Int): IntOffset =
        IntOffset(geometry.origin.x + geometry.controlSize / 2, geometry.origin.y - gap)
}
