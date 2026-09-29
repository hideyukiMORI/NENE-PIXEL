package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerLimits
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.presentation.compose.R

/**
 * Why the rename dialog refused a name (#144 U7), with the resource of the message shown under the field: a name
 * longer than `LayerLimits.MAX_NAME_CODE_POINTS` code points ([TooLong], a plural), or one holding a character a
 * name cannot hold ([Invalid]).
 */
internal enum class LayerRenameProblem(
    val message: Int,
) {
    TooLong(R.plurals.layer_rename_too_long),
    Invalid(R.string.layer_rename_invalid),
}

/**
 * The problem `LayerName.create` [rejection] shows as: too long for `LayerNameTooLong`, otherwise a character the
 * name cannot hold (a control character, or an unpaired surrogate).
 */
internal fun layerRenameProblemOf(rejection: DomainValueRejection): LayerRenameProblem =
    if (rejection is DomainValueRejection.LayerNameTooLong) LayerRenameProblem.TooLong else LayerRenameProblem.Invalid

/** The localized message; the length limit is passed as the plural quantity and its `%1$d`. */
@Composable
internal fun LayerRenameProblem.text(): String =
    when (this) {
        LayerRenameProblem.TooLong -> {
            pluralStringResource(message, LayerLimits.MAX_NAME_CODE_POINTS, LayerLimits.MAX_NAME_CODE_POINTS)
        }

        LayerRenameProblem.Invalid -> {
            stringResource(message)
        }
    }
