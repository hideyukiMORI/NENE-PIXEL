package io.github.hideyukimori.nenepixel.adapters.persistence

import android.graphics.Matrix

private const val QUARTER_TURN_DEGREES: Float = 90f
private const val HALF_TURN_DEGREES: Float = 180f

/**
 * The transform that shows a stored picture upright for an EXIF `Orientation` value (ADR 0032).
 *
 * The eight EXIF values map to the identity, the two mirror flips, the three rotations, and the two
 * rotation-plus-mirror combinations. An unknown value is the identity.
 */
internal fun referenceImageOrientationMatrix(orientation: Int): Matrix =
    Matrix().apply {
        when (orientation) {
            JpegExifOrientation.FLIP_HORIZONTAL -> {
                setScale(-1f, 1f)
            }

            JpegExifOrientation.ROTATE_180 -> {
                setRotate(HALF_TURN_DEGREES)
            }

            JpegExifOrientation.FLIP_VERTICAL -> {
                setScale(1f, -1f)
            }

            JpegExifOrientation.TRANSPOSE -> {
                setRotate(QUARTER_TURN_DEGREES)
                postScale(-1f, 1f)
            }

            JpegExifOrientation.ROTATE_90 -> {
                setRotate(QUARTER_TURN_DEGREES)
            }

            JpegExifOrientation.TRANSVERSE -> {
                setRotate(-QUARTER_TURN_DEGREES)
                postScale(-1f, 1f)
            }

            JpegExifOrientation.ROTATE_270 -> {
                setRotate(-QUARTER_TURN_DEGREES)
            }

            else -> {
                reset()
            }
        }
    }
