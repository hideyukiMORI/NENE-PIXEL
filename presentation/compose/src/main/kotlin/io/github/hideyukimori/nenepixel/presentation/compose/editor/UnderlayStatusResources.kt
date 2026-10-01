package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImageSourceRejection
import io.github.hideyukimori.nenepixel.presentation.compose.R

/** The status text for a failed or rejected reference-image pick, or null for every other outcome (ADR 0032). */
internal fun PersistenceLastOutcome.underlayFailureResource(): Int? =
    when (val failure = (this as? PersistenceLastOutcome.Failed)?.failure) {
        is PersistenceFailure.ReferenceImagePick -> R.string.underlay_pick_failed
        is PersistenceFailure.ReferenceImageRejected -> failure.reason.rejectionResource()
        else -> null
    }

private fun ReferenceImageSourceRejection.rejectionResource(): Int =
    when (this) {
        ReferenceImageSourceRejection.TooManyBytes -> R.string.underlay_too_many_bytes
        ReferenceImageSourceRejection.TooManyPixels -> R.string.underlay_too_many_pixels
        ReferenceImageSourceRejection.Unsupported -> R.string.underlay_unsupported
    }
