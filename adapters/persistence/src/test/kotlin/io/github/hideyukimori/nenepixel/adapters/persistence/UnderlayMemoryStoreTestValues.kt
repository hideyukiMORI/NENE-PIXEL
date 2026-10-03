package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayRecollection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedPlacement
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedPlacementResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayOpacity
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayVisibility
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.fail

/** Values shared by the `UnderlayMemoryStore` tests. */
internal object UnderlayMemoryStoreTestValues {
    /** The work whose id is [number] as 32 lower-case hexadecimal characters. */
    fun document(number: Int): DocumentId =
        when (val result = DocumentId.create("%032x".format(number))) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("invalid document id $number: ${result.rejection}")
        }

    /** A [width] x [height] image whose pixels all derive from [seed]. */
    fun image(
        width: Int,
        height: Int,
        seed: Int,
    ): ReferenceImage =
        when (val result = ReferenceImage.create(width, height, IntArray(width * height) { index -> seed + index })) {
            is ReferenceImageResult.Created -> result.image
            is ReferenceImageResult.Rejected -> fail("invalid image: ${result.reason}")
        }

    fun underlay(
        image: ReferenceImage,
        left: Double,
        visibility: UnderlayVisibility,
    ): RememberedUnderlay {
        val placement =
            when (val result = RememberedPlacement.create(left, -2.5, 3.0)) {
                is RememberedPlacementResult.Created -> result.placement
                is RememberedPlacementResult.Rejected -> fail("invalid placement: ${result.reason}")
            }
        return RememberedUnderlay.create(image, placement, UnderlayOpacity.create(128), visibility)
    }

    fun recalled(recollection: UnderlayRecollection): RememberedUnderlay =
        when (recollection) {
            is UnderlayRecollection.Remembered -> recollection.underlay
            UnderlayRecollection.Absent -> fail("nothing was recalled")
        }

    fun imageName(number: Int): String = UnderlayRecordNames.image(document(number).value)

    fun stateName(number: Int): String = UnderlayRecordNames.state(document(number).value)
}
