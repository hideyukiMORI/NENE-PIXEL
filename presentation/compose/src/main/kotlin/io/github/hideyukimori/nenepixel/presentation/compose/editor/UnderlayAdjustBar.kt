package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayInteraction
import io.github.hideyukimori.nenepixel.presentation.compose.R

/**
 * The bar shown at the bottom centre of the work area while the underlay is being adjusted (#171, ADR 0032
 * "Adjust mode"). It sits where the layer notice sits (16dp above the bottom, 88dp from each side, at most 360dp
 * wide) and looks like the layer panel. The first line names the mode, then "fit to drawing", which keeps the mode,
 * and "done", which leaves it; the second line is the underlay row's opacity slider. Back also leaves the mode.
 *
 * It reads the underlay through one `derivedStateOf`, so a stroke does not recompose it. The bar is a material3
 * `Surface`, which takes the pointer like the panel and the notice do, so a tap or drag on it never reaches the
 * canvas. Composed before the layer overlay, its Back handler yields to the open panel's.
 */
@Composable
internal fun UnderlayAdjustBar(
    state: State<EditorRenderState>,
    callbacks: EditorUnderlayCallbacks,
) {
    val adjusted by remember(state) { derivedStateOf { adjustedUnderlay(state.value.underlay) } }
    val underlay = adjusted
    if (underlay != null) {
        BackHandler { callbacks.onSet(underlay.rested()) }
        val scheme = MaterialTheme.colorScheme
        val title = stringResource(R.string.underlay_adjusting)
        Box(Modifier.fillMaxSize()) {
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = LayerGeometry.NOTICE_SIDE_MARGIN,
                            end = LayerGeometry.NOTICE_SIDE_MARGIN,
                            bottom = LayerGeometry.MARGIN,
                        ).widthIn(max = LayerGeometry.NOTICE_MAX_WIDTH)
                        .testTag(BAR_TAG)
                        .semantics { paneTitle = title },
                shape = RoundedCornerShape(LayerGeometry.PANEL_CORNER),
                color = scheme.surface,
                border = BorderStroke(LayerGeometry.BORDER, scheme.primary),
            ) {
                Column(Modifier.padding(LayerGeometry.PANEL_PADDING)) {
                    UnderlayAdjustHeading(title, underlay, callbacks)
                    UnderlayOpacitySlider(underlay, callbacks, identity = OPACITY_TAG)
                }
            }
        }
    }
}

/** The underlay to adjust: [underlay] while its interaction is adjusting, otherwise none. */
internal fun adjustedUnderlay(underlay: ReferenceUnderlay?): ReferenceUnderlay? =
    underlay?.takeIf { value -> value.interaction == UnderlayInteraction.Adjusting }

@Composable
private fun UnderlayAdjustHeading(
    title: String,
    underlay: ReferenceUnderlay,
    callbacks: EditorUnderlayCallbacks,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LayerGeometry.GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = { callbacks.onSet(underlay.fitted()) }, modifier = Modifier.testTag(FIT_TAG)) {
            Text(stringResource(R.string.underlay_fit))
        }
        Button(onClick = { callbacks.onSet(underlay.rested()) }, modifier = Modifier.testTag(DONE_TAG)) {
            Text(stringResource(R.string.underlay_adjust_done))
        }
    }
}

private const val BAR_TAG: String = "editor_underlay_adjust_bar"
private const val FIT_TAG: String = "editor_underlay_adjust_fit"
private const val DONE_TAG: String = "editor_underlay_adjust_done"
private const val OPACITY_TAG: String = "editor_underlay_adjust_opacity"
