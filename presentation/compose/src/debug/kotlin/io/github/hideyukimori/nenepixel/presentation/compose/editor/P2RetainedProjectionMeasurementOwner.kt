package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Bitmap
import androidx.core.graphics.get
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import java.security.MessageDigest

public class P2RetainedProjectionMeasurementOwner internal constructor(
    private val bitmap: Bitmap,
) {
    public val pixelCount: Int = bitmap.width * bitmap.height
    public val firstX: Int = 0
    public val firstY: Int = 0
    public val firstArgb: Int = bitmap[firstX, firstY]
    public val lastX: Int = bitmap.width - 1
    public val lastY: Int = bitmap.height - 1
    public val lastArgb: Int = bitmap[lastX, lastY]
    public val projectionDigestSha256: String = bitmap.projectionDigest()

    public fun mismatchCount(expectedArgb: Int): Int {
        var mismatches = 0
        repeat(bitmap.height) { y ->
            repeat(bitmap.width) { x ->
                if (bitmap[x, y] != expectedArgb) mismatches += 1
            }
        }
        return mismatches
    }
}

public fun retainP2ProjectionForMeasurement(snapshot: PixelSnapshot): P2RetainedProjectionMeasurementOwner =
    P2RetainedProjectionMeasurementOwner(snapshot.toRenderedBitmap())

private fun Bitmap.projectionDigest(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.updateInt(width * height)
    repeat(height) { y ->
        repeat(width) { x ->
            digest.updateInt(x)
            digest.updateInt(y)
            digest.updateInt(this@projectionDigest[x, y])
        }
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
