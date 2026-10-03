package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedPlacement
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedPlacementResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayOpacity
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayVisibility
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

internal class UnderlayMemoryRecordPairTest {
    @Test
    fun `a pair of records round trips to each remembered value`() {
        val original = remembered(UnderlayVisibility.Hidden)
        val imageRecord = UnderlayImageRecord.encode(original.image)
        val stateRecord = UnderlayStateRecord.encode(UnderlayImageRecord.checksum(imageRecord), original)

        val image = decodedValue(UnderlayImageRecord.decode(imageRecord))
        val state = decodedValue(UnderlayStateRecord.decode(stateRecord))
        val restored = decodedValue(state.withImage(image, UnderlayImageRecord.checksum(imageRecord)))

        assertEquals(original.image.width, restored.image.width)
        assertEquals(original.image.height, restored.image.height)
        assertArrayEquals(original.image.copyPackedRgba8888(), restored.image.copyPackedRgba8888())
        assertEquals(original.placement, restored.placement)
        assertEquals(original.opacity, restored.opacity)
        assertEquals(original.visibility, restored.visibility)
    }

    @Test
    fun `joining keeps the decoded image instance`() {
        val original = remembered(UnderlayVisibility.Shown)
        val imageRecord = UnderlayImageRecord.encode(original.image)
        val checksum = UnderlayImageRecord.checksum(imageRecord)
        val state = decodedValue(UnderlayStateRecord.decode(UnderlayStateRecord.encode(checksum, original)))

        val restored = decodedValue(state.withImage(original.image, checksum))

        assertSame(original.image, restored.image)
        assertEquals(original, restored)
    }

    @Test
    fun `a state that names another image is unreadable`() {
        val original = remembered(UnderlayVisibility.Shown)
        val imageRecord = UnderlayImageRecord.encode(original.image)
        val checksum = UnderlayImageRecord.checksum(imageRecord)
        val state = decodedValue(UnderlayStateRecord.decode(UnderlayStateRecord.encode(checksum xor 1, original)))

        assertEquals(UnderlayRecordDecodeResult.Unreadable, state.withImage(original.image, checksum))
    }

    private fun <T> decodedValue(result: UnderlayRecordDecodeResult<T>): T =
        when (result) {
            is UnderlayRecordDecodeResult.Decoded -> result.value
            UnderlayRecordDecodeResult.Unreadable -> fail("the record was unreadable")
        }

    private fun remembered(visibility: UnderlayVisibility): RememberedUnderlay {
        val pixels = intArrayOf(0x11223344, 0x55667700, -1, 0)
        val image = ReferenceImage.create(2, 2, pixels) as ReferenceImageResult.Created
        val placement = RememberedPlacement.create(-3.5, 4.25, 2.0) as RememberedPlacementResult.Created
        return RememberedUnderlay.create(image.image, placement.placement, UnderlayOpacity.create(90), visibility)
    }
}
