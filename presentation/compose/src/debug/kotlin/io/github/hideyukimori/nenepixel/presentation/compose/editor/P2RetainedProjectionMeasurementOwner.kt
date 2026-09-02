package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.graphics.toArgb
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import java.security.MessageDigest

public class P2RetainedProjectionMeasurementOwner internal constructor(
    private val pixels: List<RenderedPixel>,
) {
    public val pixelCount: Int = pixels.size
    public val firstX: Int =
        pixels
            .first()
            .position.x.value
    public val firstY: Int =
        pixels
            .first()
            .position.y.value
    public val firstArgb: Int = pixels.first().color.toArgb()
    public val lastX: Int =
        pixels
            .last()
            .position.x.value
    public val lastY: Int =
        pixels
            .last()
            .position.y.value
    public val lastArgb: Int = pixels.last().color.toArgb()
    public val projectionDigestSha256: String = pixels.projectionDigest()

    public fun mismatchCount(expectedArgb: Int): Int = pixels.count { pixel -> pixel.color.toArgb() != expectedArgb }
}

public fun retainP2ProjectionForMeasurement(snapshot: PixelSnapshot): P2RetainedProjectionMeasurementOwner =
    P2RetainedProjectionMeasurementOwner(snapshot.toRenderedPixels())

private fun List<RenderedPixel>.projectionDigest(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.updateInt(size)
    forEach { pixel ->
        digest.updateInt(pixel.position.x.value)
        digest.updateInt(pixel.position.y.value)
        digest.updateInt(pixel.color.toArgb())
    }
    return digest.digest().hex()
}

private fun MessageDigest.updateInt(value: Int) {
    update((value ushr BYTE_3_SHIFT).toByte())
    update((value ushr BYTE_2_SHIFT).toByte())
    update((value ushr BYTE_1_SHIFT).toByte())
    update(value.toByte())
}

private fun ByteArray.hex(): String =
    buildString(size * 2) {
        this@hex.forEach { byte ->
            val value = byte.toInt() and UBYTE_MASK
            append(HEX_DIGITS[value ushr NIBBLE_SHIFT])
            append(HEX_DIGITS[value and NIBBLE_MASK])
        }
    }

private const val BYTE_3_SHIFT: Int = 24
private const val BYTE_2_SHIFT: Int = 16
private const val BYTE_1_SHIFT: Int = 8
private const val UBYTE_MASK: Int = 0xff
private const val NIBBLE_MASK: Int = 0x0f
private const val NIBBLE_SHIFT: Int = 4
private const val HEX_DIGITS: String = "0123456789ABCDEF"
