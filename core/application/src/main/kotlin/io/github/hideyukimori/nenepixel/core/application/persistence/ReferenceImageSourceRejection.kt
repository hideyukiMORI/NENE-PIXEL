package io.github.hideyukimori.nenepixel.core.application.persistence

/** Why a picked source cannot become a reference image. */
public sealed interface ReferenceImageSourceRejection {
    /** The source file is larger than the byte limit. */
    public data object TooManyBytes : ReferenceImageSourceRejection

    /** The decoded picture is larger than the pixel limit. */
    public data object TooManyPixels : ReferenceImageSourceRejection

    /** The source is not a picture format the app can decode. */
    public data object Unsupported : ReferenceImageSourceRejection
}
