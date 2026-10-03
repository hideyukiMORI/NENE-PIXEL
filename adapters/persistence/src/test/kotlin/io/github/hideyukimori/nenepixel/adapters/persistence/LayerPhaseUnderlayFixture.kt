package io.github.hideyukimori.nenepixel.adapters.persistence

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.HexFormat
import javax.imageio.ImageIO

/** The sole generator of the checked, opaque #145 reference PNG; never used by the application. */
internal object LayerPhaseUnderlayFixture {
    const val SIDE: Int = 1024
    private const val SOURCE_PIXELS_PER_CELL: Int = 4
    private const val CHANNEL_FLOOR: Int = 64
    private const val CHANNEL_SPAN: Int = 192
    private const val OPAQUE_ALPHA: Int = 255
    private const val RED_SHIFT: Int = 24
    private const val GREEN_SHIFT: Int = 16
    private const val BLUE_SHIFT: Int = 8
    private const val RGBA_BYTES: Int = 4

    fun rgbaPixels(): IntArray =
        IntArray(SIDE * SIDE) { index ->
            val u = index % SIDE / SOURCE_PIXELS_PER_CELL
            val v = index / SIDE / SOURCE_PIXELS_PER_CELL
            val red = CHANNEL_FLOOR + u % CHANNEL_SPAN
            val green = CHANNEL_FLOOR + v % CHANNEL_SPAN
            val blue = CHANNEL_FLOOR + (u + v) % CHANNEL_SPAN
            (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or (blue shl BLUE_SHIFT) or OPAQUE_ALPHA
        }

    fun pngBytes(): ByteArray {
        val image = BufferedImage(SIDE, SIDE, BufferedImage.TYPE_INT_RGB)
        val rgba = rgbaPixels()
        val argb = IntArray(rgba.size) { (rgba[it] ushr BLUE_SHIFT) or (OPAQUE_ALPHA shl RED_SHIFT) }
        image.setRGB(0, 0, SIDE, SIDE, argb, 0, SIDE)
        return ByteArrayOutputStream().use { output ->
            check(ImageIO.write(image, "png", output))
            output.toByteArray()
        }
    }

    fun rgbaSha256(pixels: IntArray): String {
        val packed = ByteBuffer.allocate(pixels.size * RGBA_BYTES)
        pixels.forEach { packed.putInt(it) }
        return sha256(packed.array())
    }

    fun sha256(bytes: ByteArray): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
}
