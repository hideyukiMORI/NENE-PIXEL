package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportSourceRejection
import io.github.hideyukimori.nenepixel.presentation.compose.R

/** The status text for a failed or rejected PNG import pick, or null for every other outcome (ADR 0033). */
internal fun PersistenceLastOutcome.pngImportFailureResource(): Int? =
    when (val failure = (this as? PersistenceLastOutcome.Failed)?.failure) {
        is PersistenceFailure.PngImportPick -> R.string.png_import_failed
        is PersistenceFailure.PngImportRejected -> failure.reason.rejectionResource()
        else -> null
    }

private fun PngImportSourceRejection.rejectionResource(): Int =
    when (this) {
        PngImportSourceRejection.TooManyBytes -> R.string.png_import_too_many_bytes
        PngImportSourceRejection.TooManyPixels -> R.string.png_import_too_many_pixels
        PngImportSourceRejection.Unsupported -> R.string.png_import_unsupported
    }
