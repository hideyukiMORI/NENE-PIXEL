package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedPlacement
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedPlacementResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayOpacity
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayVisibility

/**
 * The 40-byte `<id>.state` record of ADR 0034: `NPUS`, version, the image record's CRC-32 field, left,
 * top and scale as binary64, the opacity byte, the visibility byte (0 hidden, 1 shown), and the CRC-32.
 */
internal object UnderlayStateRecord {
    /** Returns a newly allocated record of [underlay] that names the image record of CRC-32 [imageChecksum]. */
    fun encode(
        imageChecksum: Int,
        underlay: RememberedUnderlay,
    ): ByteArray {
        val bytes = ByteArray(UnderlayMemoryLayout.STATE_BYTE_COUNT)
        UnderlayMemoryLayout.writePrefix(bytes, UnderlayMemoryLayout.STATE_MAGIC)
        RecoveryRecordBigEndian.writeInt(bytes, IMAGE_CHECKSUM_OFFSET, imageChecksum)
        RecoveryRecordBigEndian.writeLong(bytes, LEFT_OFFSET, underlay.placement.left.toRawBits())
        RecoveryRecordBigEndian.writeLong(bytes, TOP_OFFSET, underlay.placement.top.toRawBits())
        RecoveryRecordBigEndian.writeLong(bytes, SCALE_OFFSET, underlay.placement.scale.toRawBits())
        bytes[OPACITY_OFFSET] = underlay.opacity.alpha.toByte()
        bytes[VISIBILITY_OFFSET] = visibilityByte(underlay.visibility).toByte()
        UnderlayMemoryLayout.seal(bytes)
        return bytes
    }

    /** Checks the length, magic, version and CRC-32, then the value ranges. */
    fun decode(bytes: ByteArray): UnderlayRecordDecodeResult<UnderlayRecordedState> =
        if (bytes.size == UnderlayMemoryLayout.STATE_BYTE_COUNT &&
            UnderlayMemoryLayout.hasPrefix(bytes, UnderlayMemoryLayout.STATE_MAGIC) &&
            UnderlayMemoryLayout.hasValidChecksum(bytes)
        ) {
            decodeValues(bytes)
        } else {
            UnderlayRecordDecodeResult.Unreadable
        }

    private fun decodeValues(bytes: ByteArray): UnderlayRecordDecodeResult<UnderlayRecordedState> {
        val alpha = RecoveryRecordBigEndian.unsignedByte(bytes[OPACITY_OFFSET])
        return if (alpha in UnderlayOpacity.MIN.alpha..UnderlayOpacity.MAX.alpha) {
            decodeVisibility(bytes, UnderlayOpacity.create(alpha))
        } else {
            UnderlayRecordDecodeResult.Unreadable
        }
    }

    private fun decodeVisibility(
        bytes: ByteArray,
        opacity: UnderlayOpacity,
    ): UnderlayRecordDecodeResult<UnderlayRecordedState> =
        when (RecoveryRecordBigEndian.unsignedByte(bytes[VISIBILITY_OFFSET])) {
            HIDDEN_BYTE -> decodePlacement(bytes, opacity, UnderlayVisibility.Hidden)
            SHOWN_BYTE -> decodePlacement(bytes, opacity, UnderlayVisibility.Shown)
            else -> UnderlayRecordDecodeResult.Unreadable
        }

    private fun decodePlacement(
        bytes: ByteArray,
        opacity: UnderlayOpacity,
        visibility: UnderlayVisibility,
    ): UnderlayRecordDecodeResult<UnderlayRecordedState> =
        when (
            val created =
                RememberedPlacement.create(
                    Double.fromBits(RecoveryRecordBigEndian.readLong(bytes, LEFT_OFFSET)),
                    Double.fromBits(RecoveryRecordBigEndian.readLong(bytes, TOP_OFFSET)),
                    Double.fromBits(RecoveryRecordBigEndian.readLong(bytes, SCALE_OFFSET)),
                )
        ) {
            is RememberedPlacementResult.Created -> {
                val imageChecksum = RecoveryRecordBigEndian.readInt(bytes, IMAGE_CHECKSUM_OFFSET)
                UnderlayRecordDecodeResult.Decoded(
                    UnderlayRecordedState(imageChecksum, created.placement, opacity, visibility),
                )
            }

            is RememberedPlacementResult.Rejected -> {
                UnderlayRecordDecodeResult.Unreadable
            }
        }

    private fun visibilityByte(visibility: UnderlayVisibility): Int =
        when (visibility) {
            UnderlayVisibility.Hidden -> HIDDEN_BYTE
            UnderlayVisibility.Shown -> SHOWN_BYTE
        }

    private const val IMAGE_CHECKSUM_OFFSET: Int = 6
    private const val LEFT_OFFSET: Int = 10
    private const val TOP_OFFSET: Int = 18
    private const val SCALE_OFFSET: Int = 26
    private const val OPACITY_OFFSET: Int = 34
    private const val VISIBILITY_OFFSET: Int = 35
    private const val HIDDEN_BYTE: Int = 0
    private const val SHOWN_BYTE: Int = 1
}
