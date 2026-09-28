package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.presentation.compose.R

/**
 * The 56dp quick-select control (#108 UI spec "Components"): a swatch of the shown slot inside a 1dp outline,
 * or a 3dp primary ring and the eyedropper mark while armed. [onClick] is the accessibility click; the pointer
 * stream is the caller's.
 */
@Composable
internal fun QuickSelectControl(
    display: QuickSelectControlDisplay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val description = stringResource(R.string.quick_select)
    val state = display.state.text()
    Box(
        modifier
            .size(QuickSelectGeometry.CONTROL_SIZE)
            .clip(CircleShape)
            .background(scheme.surface, CircleShape)
            .border(
                if (display.armed) ACTIVE_RING else OUTLINE,
                if (display.armed) scheme.primary else scheme.outline,
                CircleShape,
            ).testTag(QuickSelectSemantics.CONTROL_TAG)
            .semantics {
                role = Role.Button
                contentDescription = description
                stateDescription = state
                onClick {
                    onClick()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(CONTROL_SWATCH).background(display.swatch, CircleShape))
        if (display.armed) {
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
                EditorSymbol(EditorIcon.Eyedropper)
            }
        }
    }
}

private val CONTROL_SWATCH = 32.dp
private val OUTLINE = 1.dp
private val ACTIVE_RING = 3.dp
