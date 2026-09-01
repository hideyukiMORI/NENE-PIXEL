package io.github.hideyukimori.nenepixel.core.pixelengine

import org.junit.jupiter.api.fail

internal object StrokeRasterizationAssertions {
    fun rasterized(result: StrokeRasterizationResult): PixelPatch =
        when (result) {
            is StrokeRasterizationResult.Rasterized -> result.patch
            StrokeRasterizationResult.NoChanges -> fail("Expected Rasterized but was NoChanges.")
            is StrokeRasterizationResult.Rejected -> fail("Expected Rasterized but was Rejected(${result.rejection}).")
        }

    fun rejected(result: StrokeRasterizationResult): StrokeRasterizationRejection =
        when (result) {
            is StrokeRasterizationResult.Rasterized -> fail("Expected Rejected but was Rasterized(${result.patch}).")
            StrokeRasterizationResult.NoChanges -> fail("Expected Rejected but was NoChanges.")
            is StrokeRasterizationResult.Rejected -> result.rejection
        }
}
