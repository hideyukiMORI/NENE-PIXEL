package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure
import io.github.hideyukimori.nenepixel.core.projectformat.palette.PaletteJsonRejection

internal object PaletteJsonRejectionMapper {
    fun map(rejection: PaletteJsonRejection): ProjectStorageFailure =
        when (rejection) {
            is PaletteJsonRejection.ResourceLimitExceeded -> ProjectStorageFailure.ResourceLimitExceeded

            PaletteJsonRejection.UnsupportedFormat,
            PaletteJsonRejection.UnsupportedVersion,
            -> ProjectStorageFailure.UnsupportedPaletteJsonVersion

            PaletteJsonRejection.InvalidUtf8,
            is PaletteJsonRejection.InvalidJson,
            is PaletteJsonRejection.IntegerOverflow,
            PaletteJsonRejection.UnknownField,
            PaletteJsonRejection.DuplicateField,
            PaletteJsonRejection.MissingField,
            is PaletteJsonRejection.InvalidColor,
            PaletteJsonRejection.TooManyColors,
            is PaletteJsonRejection.InvalidDefinition,
            -> ProjectStorageFailure.InvalidPaletteJson
        }
}
