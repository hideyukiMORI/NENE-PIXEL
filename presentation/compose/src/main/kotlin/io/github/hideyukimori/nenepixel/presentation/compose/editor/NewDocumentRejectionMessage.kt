package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentDimension
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRejection
import io.github.hideyukimori.nenepixel.presentation.compose.R

@Composable
internal fun NewDocumentRejection.userMessage(): String {
    val name = stringResource(dimension.labelResource())
    return when (this) {
        is NewDocumentRejection.Required -> {
            stringResource(R.string.required_dimension, name)
        }

        is NewDocumentRejection.NotDecimalInteger -> {
            stringResource(R.string.integer_dimension, name)
        }

        is NewDocumentRejection.IntegerOverflow -> {
            stringResource(R.string.overflow_dimension, name)
        }

        is NewDocumentRejection.OutsideSupportedRange -> {
            stringResource(R.string.range_dimension, name, minimum, maximum)
        }
    }
}

private val NewDocumentRejection.dimension: NewDocumentDimension
    get() =
        when (this) {
            is NewDocumentRejection.Required -> dimension
            is NewDocumentRejection.NotDecimalInteger -> dimension
            is NewDocumentRejection.IntegerOverflow -> dimension
            is NewDocumentRejection.OutsideSupportedRange -> dimension
        }

internal fun NewDocumentDimension.labelResource(): Int =
    when (this) {
        NewDocumentDimension.Width -> R.string.width
        NewDocumentDimension.Height -> R.string.height
    }
