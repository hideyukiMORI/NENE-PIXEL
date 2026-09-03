package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentDimension
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRejection

internal fun NewDocumentRejection.toUserMessage(): String {
    val name = dimension.displayName()
    return when (this) {
        is NewDocumentRejection.Required -> {
            "$name is required."
        }

        is NewDocumentRejection.NotDecimalInteger -> {
            "$name must be a whole number."
        }

        is NewDocumentRejection.IntegerOverflow -> {
            "$name is too large."
        }

        is NewDocumentRejection.OutsideSupportedRange -> {
            "$name must be between $minimum and $maximum."
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

private fun NewDocumentDimension.displayName(): String =
    when (this) {
        NewDocumentDimension.Width -> "Width"
        NewDocumentDimension.Height -> "Height"
    }
