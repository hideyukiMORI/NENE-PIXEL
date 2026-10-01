package io.github.hideyukimori.nenepixel.core.application.workspace.underlay

public sealed interface ReferenceImageResult {
    public data class Created internal constructor(
        public val image: ReferenceImage,
    ) : ReferenceImageResult

    public data class Rejected internal constructor(
        public val reason: ReferenceImageRejection,
    ) : ReferenceImageResult
}
