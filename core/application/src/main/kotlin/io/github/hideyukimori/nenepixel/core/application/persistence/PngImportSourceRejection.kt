package io.github.hideyukimori.nenepixel.core.application.persistence

/** Why a picked file cannot become an imported raster (ADR 0033). */
public sealed interface PngImportSourceRejection {
    /** The file is larger than 8 MB. */
    public data object TooManyBytes : PngImportSourceRejection

    /** A side of the picture is larger than 1024 pixels. */
    public data object TooManyPixels : PngImportSourceRejection

    /** The PNG is outside the accepted content, or its data is malformed. */
    public data object Unsupported : PngImportSourceRejection
}
