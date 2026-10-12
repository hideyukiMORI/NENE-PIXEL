package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.presentation.compose.R
import kotlin.math.roundToInt

/** Issue #168: SV, hue and exact-RGB alpha controls share the session's current RGBA. */
@Composable
internal fun PaletteColorPicker(
    model: PaletteColorEditorModel,
    callbacks: PaletteColorEditCallbacks,
) {
    val coordinates = remember { PalettePickerCoordinates(model.color) }
    SideEffect { coordinates.synchronize(model.color, model.colorEditActive) }
    val controls = PalettePickerControls(model, coordinates, callbacks)
    val drawing = remember { PalettePickerDrawing() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PalettePickerPlane(controls, drawing)
        PalettePickerBarControl(PalettePickerAxis.Hue, controls, drawing)
        PalettePickerBarControl(PalettePickerAxis.Alpha, controls, drawing)
    }
}

@Composable
private fun PalettePickerPlane(
    controls: PalettePickerControls,
    drawing: PalettePickerDrawing,
) {
    val hsv = controls.hsv
    val description = stringResource(R.string.palette_picker_sv_value, percent(hsv.saturation), percent(hsv.value))
    val actions = controls.planeActions()
    val interaction = rememberUpdatedState(controls.interaction(controls::planeAt))
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(144.dp)
            .editorDescription(R.string.palette_picker_sv)
            .semantics {
                stateDescription = description
                if (!controls.model.pickerEnabled) disabled()
                customActions = actions
            }.palettePickerGesture(interaction),
    ) { drawing.plane(this, hsv) }
}

@Composable
private fun PalettePickerBarControl(
    axis: PalettePickerAxis,
    controls: PalettePickerControls,
    drawing: PalettePickerDrawing,
) {
    val bar = controls.bar(axis)
    val label = if (axis == PalettePickerAxis.Hue) R.string.palette_picker_hue else R.string.palette_picker_opacity
    val value = stringResource(R.string.palette_picker_percent, percent(bar.fraction))
    val interaction = rememberUpdatedState(controls.interaction { point, size -> controls.barAt(axis, point, size) })
    Column {
        Text(stringResource(label))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .editorDescription(label)
                .semantics {
                    stateDescription = value
                    progressBarRangeInfo = ProgressBarRangeInfo(bar.fraction, 0f..1f)
                    if (!controls.model.pickerEnabled) disabled()
                    setProgress { controls.stepBar(axis, it) }
                }.palettePickerGesture(interaction),
        ) { drawing.bar(this, bar) }
    }
}

private const val FULL_PERCENT: Int = 100

private fun percent(value: Float): Int = (value * FULL_PERCENT).roundToInt()

/** Bounded coordinate calculations and intents; this object reads canonical RGBA, never owns it. */
private class PalettePickerControls(
    val model: PaletteColorEditorModel,
    private val coordinates: PalettePickerCoordinates,
    private val callbacks: PaletteColorEditCallbacks,
) {
    val hsv: PaletteHsvColor get() = coordinates.current(model.color, model.colorEditActive)

    fun interaction(change: (Offset, IntSize) -> Unit): PalettePickerInteraction =
        PalettePickerInteraction(model.pickerEnabled, callbacks, change)

    fun planeAt(
        point: Offset,
        size: IntSize,
    ) {
        val next = hsv.copy(saturation = fraction(point.x, size.width), value = 1f - fraction(point.y, size.height))
        callbacks.update(coordinates.colorAt(next, model.color))
    }

    fun barAt(
        axis: PalettePickerAxis,
        point: Offset,
        size: IntSize,
    ) {
        callbacks.update(barColor(axis, fraction(point.x, size.width)))
    }

    fun stepBar(
        axis: PalettePickerAxis,
        fraction: Float,
    ): Boolean = model.pickerEnabled && callbacks.step(barColor(axis, fraction.coerceIn(0f, 1f)))

    fun bar(axis: PalettePickerAxis): PalettePickerBar =
        when (axis) {
            PalettePickerAxis.Hue -> {
                PalettePickerBar(axis, hsv.hue / PaletteHsvColor.FULL_HUE, HUE_COLORS)
            }

            PalettePickerAxis.Alpha -> {
                val opaque = model.color.toComposeColor().copy(alpha = 1f)
                PalettePickerBar(
                    axis,
                    model.color.alpha.value
                        .toInt()
                        .toFloat() / PaletteHsvColor.CHANNEL_MAX,
                    listOf(opaque.copy(alpha = 0f), opaque),
                )
            }
        }

    @Composable
    fun planeActions(): List<CustomAccessibilityAction> =
        listOf(
            CustomAccessibilityAction(stringResource(R.string.palette_picker_more_saturation)) { stepPlane(STEP, 0f) },
            CustomAccessibilityAction(stringResource(R.string.palette_picker_less_saturation)) { stepPlane(-STEP, 0f) },
            CustomAccessibilityAction(stringResource(R.string.palette_picker_more_value)) { stepPlane(0f, STEP) },
            CustomAccessibilityAction(stringResource(R.string.palette_picker_less_value)) { stepPlane(0f, -STEP) },
        )

    private fun stepPlane(
        saturation: Float,
        value: Float,
    ): Boolean {
        if (!model.pickerEnabled) return false
        val next =
            hsv.copy(
                saturation = (hsv.saturation + saturation).coerceIn(0f, 1f),
                value = (hsv.value + value).coerceIn(0f, 1f),
            )
        return callbacks.step(coordinates.colorAt(next, model.color))
    }

    private fun barColor(
        axis: PalettePickerAxis,
        fraction: Float,
    ): PixelColor =
        when (axis) {
            PalettePickerAxis.Hue -> {
                coordinates.colorAt(
                    hsv.copy(hue = fraction * PaletteHsvColor.FULL_HUE),
                    model.color,
                )
            }

            PalettePickerAxis.Alpha -> {
                PaletteHsvColor.withAlpha(model.color, fraction)
            }
        }

    private fun fraction(
        position: Float,
        extent: Int,
    ): Float = (position / extent.coerceAtLeast(1)).coerceIn(0f, 1f)

    companion object {
        private const val STEP: Float = 0.05f
        private val HUE_COLORS =
            listOf(
                androidx.compose.ui.graphics.Color.Red,
                androidx.compose.ui.graphics.Color.Yellow,
                androidx.compose.ui.graphics.Color.Green,
                androidx.compose.ui.graphics.Color.Cyan,
                androidx.compose.ui.graphics.Color.Blue,
                androidx.compose.ui.graphics.Color.Magenta,
                androidx.compose.ui.graphics.Color.Red,
            )
    }
}
