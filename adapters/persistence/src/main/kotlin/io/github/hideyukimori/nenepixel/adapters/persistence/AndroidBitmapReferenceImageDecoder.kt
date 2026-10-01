package io.github.hideyukimori.nenepixel.adapters.persistence

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Matrix
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decodes PNG, JPEG, and WebP sources with the platform [BitmapFactory] (ADR 0032).
 *
 * The source size is read before any pixel is allocated. The picture is decoded with a power-of-two
 * sample size, turned upright by the EXIF orientation of a JPEG file ([JpegExifOrientation]), scaled
 * down to fit [ReferenceImageLimits.MAX_RESULT_SIDE] in one transform, and read as straight sRGB
 * RGBA8888. PNG and WebP orientation metadata is ignored.
 */
internal object AndroidBitmapReferenceImageDecoder : ReferenceImageDecoder {
    private const val CHANNEL_BITS: Int = 8
    private const val ALPHA_SHIFT: Int = 24
    private const val JPEG_MIME_TYPE: String = "image/jpeg"

    override fun decode(encoded: ByteArray): ReferenceImageDecodeResult =
        if (encoded.isEmpty()) {
            ReferenceImageDecodeResult.Unsupported
        } else {
            try {
                fromBounds(encoded, readBounds(encoded))
            } catch (_: OutOfMemoryError) {
                ReferenceImageDecodeResult.TooManyPixels
            } catch (_: IllegalArgumentException) {
                ReferenceImageDecodeResult.Unsupported
            } catch (_: IllegalStateException) {
                ReferenceImageDecodeResult.Unsupported
            }
        }

    private fun readBounds(encoded: ByteArray): BitmapFactory.Options {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(encoded, 0, encoded.size, bounds)
        return bounds
    }

    private fun fromBounds(
        encoded: ByteArray,
        bounds: BitmapFactory.Options,
    ): ReferenceImageDecodeResult {
        val mimeType = bounds.outMimeType.orEmpty()
        val longSide = max(bounds.outWidth, bounds.outHeight)
        return when {
            bounds.outWidth <= 0 || bounds.outHeight <= 0 || mimeType !in ReferenceImageLimits.SUPPORTED_MIME_TYPES -> {
                ReferenceImageDecodeResult.Unsupported
            }

            longSide > ReferenceImageLimits.MAX_SOURCE_SIDE -> {
                ReferenceImageDecodeResult.TooManyPixels
            }

            else -> {
                fromSource(encoded, mimeType, longSide)
            }
        }
    }

    private fun fromSource(
        encoded: ByteArray,
        mimeType: String,
        longSide: Int,
    ): ReferenceImageDecodeResult {
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize(longSide)
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
            }
        val source = BitmapFactory.decodeByteArray(encoded, 0, encoded.size, options)
        return if (source == null) {
            ReferenceImageDecodeResult.Unsupported
        } else {
            fromBitmap(source, orientationOf(encoded, mimeType))
        }
    }

    /** The largest power of two that keeps the sampled long side at or above the result limit. */
    private fun sampleSize(longSide: Int): Int {
        var sample = 1
        while (longSide / (sample * 2) >= ReferenceImageLimits.MAX_RESULT_SIDE) {
            sample *= 2
        }
        return sample
    }

    private fun orientationOf(
        encoded: ByteArray,
        mimeType: String,
    ): Int =
        if (mimeType == JPEG_MIME_TYPE) {
            JpegExifOrientation.orientation(encoded)
        } else {
            JpegExifOrientation.NORMAL
        }

    private fun fromBitmap(
        source: Bitmap,
        orientation: Int,
    ): ReferenceImageDecodeResult =
        try {
            val output = transformed(source, orientation)
            try {
                packed(output)
            } finally {
                if (output !== source) output.recycle()
            }
        } finally {
            source.recycle()
        }

    private fun transformed(
        source: Bitmap,
        orientation: Int,
    ): Bitmap {
        val width = source.width
        val height = source.height
        val scale = min(1f, ReferenceImageLimits.MAX_RESULT_SIDE.toFloat() / max(width, height))
        val matrix =
            Matrix().apply {
                setScale(scaledSide(width, scale).toFloat() / width, scaledSide(height, scale).toFloat() / height)
                postConcat(referenceImageOrientationMatrix(orientation))
            }
        return Bitmap.createBitmap(source, 0, 0, width, height, matrix, true)
    }

    private fun scaledSide(
        side: Int,
        scale: Float,
    ): Int = (side * scale).roundToInt().coerceIn(1, ReferenceImageLimits.MAX_RESULT_SIDE)

    private fun packed(bitmap: Bitmap): ReferenceImageDecodeResult {
        val width = bitmap.width
        val height = bitmap.height
        return if (width in 1..ReferenceImageLimits.MAX_RESULT_SIDE &&
            height in 1..ReferenceImageLimits.MAX_RESULT_SIDE
        ) {
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            for (index in pixels.indices) {
                pixels[index] = argbToRgba(pixels[index])
            }
            ReferenceImageDecodeResult.Decoded(width, height, pixels)
        } else {
            ReferenceImageDecodeResult.Unsupported
        }
    }

    /** Moves the alpha byte of a straight `0xAARRGGBB` color to the low byte: `0xRRGGBBAA`. */
    private fun argbToRgba(argb: Int): Int = (argb shl CHANNEL_BITS) or (argb ushr ALPHA_SHIFT)
}
