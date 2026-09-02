package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.toArgb
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.canvas
import io.github.hideyukimori.nenepixel.presentation.compose.requiredValue
import org.junit.jupiter.api.Assertions.assertEquals

internal class P2RenderProjectionFixture private constructor(
    private val descriptor: P2RenderProjectionDescriptor,
    private val snapshot: PixelSnapshot,
    private val expectedArgb: IntArray,
    val sourcePixelDigest: String,
    val projectionDigest: String,
    val colorCardinality: Int,
) {
    val sourceRevision: Long = snapshot.revision.value
    val firstArgb: Int = expectedArgb.first()
    val lastArgb: Int = expectedArgb.last()

    fun prepare(): P2HostMeasuredOperation<List<RenderedPixel>, P2RenderProjectionCorrectness> {
        val sourceBefore = sourceEvidence()
        assertExpectedSource(sourceBefore)
        var verified: P2RenderProjectionCorrectness? = null
        return P2HostMeasuredOperation(
            execute = snapshot::toRenderedPixels,
            verify = { result -> verified = verify(result, sourceBefore) },
            deterministicKey = { requireNotNull(verified) },
        )
    }

    private fun verify(
        pixels: List<RenderedPixel>,
        sourceBefore: P2RenderProjectionSourceEvidence,
    ): P2RenderProjectionCorrectness {
        val actualProjectionDigest = verifyProjection(pixels)
        val sourceAfter = sourceEvidence()
        assertExpectedSource(sourceAfter)
        assertEquals(sourceBefore, sourceAfter, "Projection mutated its source snapshot.")
        return P2RenderProjectionCorrectness(
            sourceRevision = sourceAfter.revision,
            sourcePixelDigest = sourceAfter.pixelDigest,
            projectionDigest = actualProjectionDigest,
            status = CORRECTNESS_PASS,
        )
    }

    private fun verifyProjection(pixels: List<RenderedPixel>): String {
        assertEquals(descriptor.shape.pixelCount, pixels.size, "Projected list count changed.")
        pixels.forEachIndexed(::verifyPixel)
        verifyEndpoint(pixels.first(), 0, "first")
        verifyEndpoint(pixels.last(), expectedArgb.lastIndex, "last")
        val actualDigest = P2RenderProjectionDigest.actualProjection(pixels)
        assertEquals(projectionDigest, actualDigest, "Full row-major projection digest changed.")
        return actualDigest
    }

    private fun verifyPixel(
        index: Int,
        pixel: RenderedPixel,
    ) {
        val expectedX = index % descriptor.shape.width
        val expectedY = index / descriptor.shape.width
        assertEquals(expectedX, pixel.position.x.value, "Projected x changed at row-major index $index.")
        assertEquals(expectedY, pixel.position.y.value, "Projected y changed at row-major index $index.")
        assertEquals(expectedArgb[index], pixel.color.toArgb(), "Projected AARRGGBB changed at index $index.")
        assertEquals(ColorSpaces.Srgb, pixel.color.colorSpace, "Projected color space changed at index $index.")
    }

    private fun verifyEndpoint(
        pixel: RenderedPixel,
        index: Int,
        label: String,
    ) {
        assertEquals(index % descriptor.shape.width, pixel.position.x.value, "$label projected x changed.")
        assertEquals(index / descriptor.shape.width, pixel.position.y.value, "$label projected y changed.")
        assertEquals(expectedArgb[index], pixel.color.toArgb(), "$label projected AARRGGBB changed.")
    }

    private fun sourceEvidence(): P2RenderProjectionSourceEvidence =
        P2RenderProjectionSourceEvidence(
            revision = snapshot.revision.value,
            pixelDigest = P2RenderProjectionDigest.actualSource(snapshot),
        )

    private fun assertExpectedSource(actual: P2RenderProjectionSourceEvidence) {
        assertEquals(sourceRevision, actual.revision, "Prepared source revision changed.")
        assertEquals(sourcePixelDigest, actual.pixelDigest, "Prepared source pixel digest changed.")
    }

    companion object {
        fun create(descriptor: P2RenderProjectionDescriptor): P2RenderProjectionFixture {
            val expectedArgb = IntArray(descriptor.shape.pixelCount, descriptor.content::argbAt)
            val snapshot = createSnapshot(descriptor, expectedArgb)
            val cardinality = expectedArgb.toSet().size
            assertEquals(descriptor.content.expectedColorCardinality(descriptor.shape.pixelCount), cardinality)
            return P2RenderProjectionFixture(
                descriptor = descriptor,
                snapshot = snapshot,
                expectedArgb = expectedArgb,
                sourcePixelDigest = P2RenderProjectionDigest.expectedSource(expectedArgb),
                projectionDigest = P2RenderProjectionDigest.expectedProjection(descriptor, expectedArgb),
                colorCardinality = cardinality,
            )
        }

        private fun createSnapshot(
            descriptor: P2RenderProjectionDescriptor,
            expectedArgb: IntArray,
        ): PixelSnapshot =
            PixelSnapshot
                .create(
                    size = canvas(descriptor.shape.width, descriptor.shape.height),
                    revision = Revision.initial(),
                    pixels = expectedArgb.map(::pixelColor),
                ).requiredValue()

        private fun pixelColor(argb: Int): PixelColor =
            PixelColor.create(
                channel(argb ushr RED_SHIFT),
                channel(argb ushr GREEN_SHIFT),
                channel(argb),
                channel(argb ushr ALPHA_SHIFT),
            )

        private fun channel(value: Int): ColorChannel = ColorChannel.create(value and UBYTE_MASK).requiredValue()

        private const val CORRECTNESS_PASS: String = "pass"
        private const val UBYTE_MASK: Int = 0xff
        private const val ALPHA_SHIFT: Int = 24
        private const val RED_SHIFT: Int = 16
        private const val GREEN_SHIFT: Int = 8
    }
}

internal data class P2RenderProjectionSourceEvidence(
    val revision: Long,
    val pixelDigest: String,
)

internal data class P2RenderProjectionCorrectness(
    val sourceRevision: Long,
    val sourcePixelDigest: String,
    val projectionDigest: String,
    val status: String,
)
