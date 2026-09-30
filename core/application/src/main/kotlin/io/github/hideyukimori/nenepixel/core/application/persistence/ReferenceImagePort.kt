package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage

/** Picks one reference image to show under the drawing (ADR 0032). */
public fun interface ReferenceImagePort {
    public suspend fun pick(): ReferenceImageOutcome
}

public sealed interface ReferenceImageOutcome {
    public data class Picked(
        public val image: ReferenceImage,
    ) : ReferenceImageOutcome

    public data object Cancelled : ReferenceImageOutcome

    public data class Rejected(
        public val reason: ReferenceImageSourceRejection,
    ) : ReferenceImageOutcome

    public data class Failed(
        public val failure: ProjectStorageFailure,
    ) : ReferenceImageOutcome
}
