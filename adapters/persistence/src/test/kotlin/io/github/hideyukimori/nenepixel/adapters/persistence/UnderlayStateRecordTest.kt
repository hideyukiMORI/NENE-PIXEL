package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedPlacement
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedPlacementResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayOpacity
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayVisibility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.util.zip.CRC32

internal class UnderlayStateRecordTest {
    @Test
    fun `shown state matches exact golden bytes`() {
        val encoded = UnderlayStateRecord.encode(IMAGE_CHECKSUM, underlay(1.5, -2.0, 3.0))

        assertEquals(GOLDEN_STATE_HEX, encoded.toHexadecimal())
        assertEquals(40, encoded.size)
        val independent = CRC32()
        independent.update(encoded, 0, encoded.size - 4)
        assertEquals(0xf9972671L, independent.value)
    }

    @Test
    fun `hidden state at the lowest opacity writes negative zero as zero`() {
        val placement = placement(-0.0, -0.0, 1.0)
        val underlay = RememberedUnderlay.create(image(), placement, UnderlayOpacity.MIN, UnderlayVisibility.Hidden)

        val encoded = UnderlayStateRecord.encode(IMAGE_CHECKSUM, underlay)

        assertEquals(HIDDEN_STATE_HEX, encoded.toHexadecimal())
    }

    @Test
    fun `golden bytes decode to each value`() {
        val state = decoded(GOLDEN_STATE_HEX.decodeHex())

        assertEquals(IMAGE_CHECKSUM, state.imageChecksum)
        assertEquals(1.5, state.placement.left)
        assertEquals(-2.0, state.placement.top)
        assertEquals(3.0, state.placement.scale)
        assertEquals(UnderlayOpacity.create(OPACITY_ALPHA), state.opacity)
        assertEquals(UnderlayVisibility.Shown, state.visibility)
    }

    @Test
    fun `hidden state at the highest opacity round trips`() {
        val placement = placement(-12.25, 7.75, 0.125)
        val underlay = RememberedUnderlay.create(image(), placement, UnderlayOpacity.MAX, UnderlayVisibility.Hidden)

        val state = decoded(UnderlayStateRecord.encode(IMAGE_CHECKSUM, underlay))

        assertEquals(IMAGE_CHECKSUM, state.imageChecksum)
        assertEquals(placement, state.placement)
        assertEquals(UnderlayOpacity.MAX, state.opacity)
        assertEquals(UnderlayVisibility.Hidden, state.visibility)
    }

    @Test
    fun `a record one byte short is unreadable`() {
        assertUnreadable(GOLDEN_STATE_HEX.decodeHex().copyOf(39))
    }

    @Test
    fun `a record one byte long is unreadable`() {
        assertUnreadable(GOLDEN_STATE_HEX.decodeHex().copyOf(41))
    }

    @Test
    fun `another magic is unreadable`() {
        assertUnreadable(changed(3, 0x49))
    }

    @Test
    fun `version two is unreadable`() {
        assertUnreadable(changed(5, 2))
    }

    @Test
    fun `a wrong checksum is unreadable`() {
        val bytes = GOLDEN_STATE_HEX.decodeHex()
        bytes[39] = (bytes[39].toInt() xor 1).toByte()

        assertUnreadable(bytes)
    }

    @Test
    fun `opacity 25 is unreadable`() {
        assertUnreadable(changed(34, 25))
    }

    @Test
    fun `visibility 2 is unreadable`() {
        assertUnreadable(changed(35, 2))
    }

    @Test
    fun `a NaN left is unreadable`() {
        val bytes = GOLDEN_STATE_HEX.decodeHex()
        "7ff8000000000000".decodeHex().copyInto(bytes, 10)

        assertUnreadable(resealed(bytes))
    }

    @Test
    fun `scale zero is unreadable`() {
        val bytes = GOLDEN_STATE_HEX.decodeHex()
        ByteArray(8).copyInto(bytes, 26)

        assertUnreadable(resealed(bytes))
    }

    private fun changed(
        offset: Int,
        value: Int,
    ): ByteArray {
        val bytes = GOLDEN_STATE_HEX.decodeHex()
        bytes[offset] = value.toByte()
        return resealed(bytes)
    }

    private fun resealed(bytes: ByteArray): ByteArray {
        val checksum = CRC32()
        checksum.update(bytes, 0, bytes.size - 4)
        val value = checksum.value
        repeat(4) { index ->
            bytes[bytes.size - 4 + index] = (value ushr ((3 - index) * 8)).toByte()
        }
        return bytes
    }

    private fun assertUnreadable(bytes: ByteArray) {
        assertEquals(UnderlayRecordDecodeResult.Unreadable, UnderlayStateRecord.decode(bytes))
    }

    private fun decoded(bytes: ByteArray): UnderlayRecordedState {
        val result = UnderlayStateRecord.decode(bytes)
        return when (result) {
            is UnderlayRecordDecodeResult.Decoded -> result.value
            UnderlayRecordDecodeResult.Unreadable -> fail("the record was unreadable")
        }
    }

    private fun underlay(
        left: Double,
        top: Double,
        scale: Double,
    ): RememberedUnderlay =
        RememberedUnderlay.create(
            image(),
            placement(left, top, scale),
            UnderlayOpacity.create(OPACITY_ALPHA),
            UnderlayVisibility.Shown,
        )

    private fun placement(
        left: Double,
        top: Double,
        scale: Double,
    ): RememberedPlacement {
        val created = RememberedPlacement.create(left, top, scale) as RememberedPlacementResult.Created
        return created.placement
    }

    private fun image(): ReferenceImage {
        val created = ReferenceImage.create(1, 1, intArrayOf(0x11223344)) as ReferenceImageResult.Created
        return created.image
    }

    private fun ByteArray.toHexadecimal(): String = joinToString("") { byte -> "%02x".format(byte) }

    private fun String.decodeHex(): ByteArray =
        ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }

    private companion object {
        const val IMAGE_CHECKSUM: Int = 0x5998be12
        const val OPACITY_ALPHA: Int = 200
        const val GOLDEN_STATE_HEX: String =
            "4e50555300015998be12" + "3ff8000000000000" + "c000000000000000" + "4008000000000000" + "c801" +
                "f9972671"
        const val HIDDEN_STATE_HEX: String =
            "4e50555300015998be12" + "0000000000000000" + "0000000000000000" + "3ff0000000000000" + "1a00" +
                "794c7510"
    }
}
