package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedPlacement
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayOpacity
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayVisibility

/** The decoded `<id>.state` record: the CRC-32 of the image record it names and the underlay values. */
internal data class UnderlayRecordedState(
    val imageChecksum: Int,
    val placement: RememberedPlacement,
    val opacity: UnderlayOpacity,
    val visibility: UnderlayVisibility,
) {
    /**
     * Joins this state with the decoded [image] of an image record whose CRC-32 field is [imageChecksum];
     * a state that names another image is [UnderlayRecordDecodeResult.Unreadable].
     */
    fun withImage(
        image: ReferenceImage,
        imageChecksum: Int,
    ): UnderlayRecordDecodeResult<RememberedUnderlay> =
        if (imageChecksum == this.imageChecksum) {
            UnderlayRecordDecodeResult.Decoded(RememberedUnderlay.create(image, placement, opacity, visibility))
        } else {
            UnderlayRecordDecodeResult.Unreadable
        }
}
