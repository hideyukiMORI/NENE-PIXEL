package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.graphics.toArgb
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.position
import io.github.hideyukimori.nenepixel.presentation.compose.requiredValue
import java.security.MessageDigest

internal object P2RenderProjectionDigest {
    fun expectedSource(argb: IntArray): String {
        val digest = sha256()
        argb.forEach { value -> digest.updateInt(value) }
        return digest.hex()
    }

    fun actualSource(snapshot: PixelSnapshot): String {
        val digest = sha256()
        repeat(snapshot.size.height.value) { y ->
            repeat(snapshot.size.width.value) { x ->
                digest.updateInt(snapshot.colorAt(position(x, y)).requiredValue().argb)
            }
        }
        return digest.hex()
    }

    fun expectedProjection(
        descriptor: P2RenderProjectionDescriptor,
        argb: IntArray,
    ): String {
        val digest = sha256()
        digest.updateInt(argb.size)
        argb.indices.forEach { index ->
            digest.updateInt(index % descriptor.shape.width)
            digest.updateInt(index / descriptor.shape.width)
            digest.updateInt(argb[index])
        }
        return digest.hex()
    }

    fun actualProjection(pixels: List<RenderedPixel>): String {
        val digest = sha256()
        digest.updateInt(pixels.size)
        pixels.forEach { pixel ->
            digest.updateInt(pixel.position.x.value)
            digest.updateInt(pixel.position.y.value)
            digest.updateInt(pixel.color.toArgb())
        }
        return digest.hex()
    }

    fun argbHex(argb: Int): String =
        argb
            .toUInt()
            .toString(HEX_RADIX)
            .uppercase()
            .padStart(ARGB_HEX_LENGTH, '0')

    private val PixelColor.argb: Int
        get() =
            (alpha.value.toInt() shl ALPHA_SHIFT) or
                (red.value.toInt() shl RED_SHIFT) or
                (green.value.toInt() shl GREEN_SHIFT) or
                blue.value.toInt()

    private fun sha256(): MessageDigest = MessageDigest.getInstance("SHA-256")

    private fun MessageDigest.updateInt(value: Int) {
        update((value ushr BYTE_3_SHIFT).toByte())
        update((value ushr BYTE_2_SHIFT).toByte())
        update((value ushr BYTE_1_SHIFT).toByte())
        update(value.toByte())
    }

    private fun MessageDigest.hex(): String = digest().hex()

    private fun ByteArray.hex(): String =
        buildString(size * 2) {
            this@hex.forEach { byte ->
                val value = byte.toInt() and UBYTE_MASK
                append(HEX_DIGITS[value ushr NIBBLE_SHIFT])
                append(HEX_DIGITS[value and NIBBLE_MASK])
            }
        }

    private const val HEX_RADIX: Int = 16
    private const val UBYTE_MASK: Int = 0xff
    private const val NIBBLE_MASK: Int = 0x0f
    private const val NIBBLE_SHIFT: Int = 4
    private const val HEX_DIGITS: String = "0123456789ABCDEF"
    private const val ARGB_HEX_LENGTH: Int = 8
    private const val ALPHA_SHIFT: Int = 24
    private const val RED_SHIFT: Int = 16
    private const val GREEN_SHIFT: Int = 8
    private const val BYTE_3_SHIFT: Int = 24
    private const val BYTE_2_SHIFT: Int = 16
    private const val BYTE_1_SHIFT: Int = 8
}
