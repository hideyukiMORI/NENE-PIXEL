package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import org.junit.jupiter.api.fail

internal object PixelPatchAssertions {
    fun created(result: PixelPatchCreationResult): PixelPatch =
        when (result) {
            is PixelPatchCreationResult.Created -> result.patch
            is PixelPatchCreationResult.Rejected -> fail("Expected Created but was Rejected(${result.rejection}).")
        }

    fun creationRejected(result: PixelPatchCreationResult): PixelPatchCreationRejection =
        when (result) {
            is PixelPatchCreationResult.Created -> fail("Expected Rejected but was Created(${result.patch}).")
            is PixelPatchCreationResult.Rejected -> result.rejection
        }

    fun applied(result: PixelPatchApplicationResult): PixelSnapshot =
        when (result) {
            is PixelPatchApplicationResult.Applied -> result.snapshot
            is PixelPatchApplicationResult.Rejected -> fail("Expected Applied but was Rejected(${result.rejection}).")
        }

    fun applicationRejected(result: PixelPatchApplicationResult): PixelPatchApplicationRejection =
        when (result) {
            is PixelPatchApplicationResult.Applied -> fail("Expected Rejected but was Applied(${result.snapshot}).")
            is PixelPatchApplicationResult.Rejected -> result.rejection
        }
}
