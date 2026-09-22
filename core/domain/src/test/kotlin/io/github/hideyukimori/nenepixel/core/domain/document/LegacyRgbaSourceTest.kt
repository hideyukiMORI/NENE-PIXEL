package io.github.hideyukimori.nenepixel.core.domain.document

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.rejected
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.canvasSize
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.pixelPosition
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class LegacyRgbaSourceTest {
    @Test
    fun `legacy source owns exact packed values and exposes defensive bulk copy`() {
        val input = intArrayOf(0x01020300, -1)
        val source = created(LegacyRgbaSource.createPackedRgba8888(ID, Revision.initial(), canvasSize(2, 1), input))
        input[0] = 0
        val output = source.copyPackedRgba8888()
        output[1] = 0

        assertEquals(PixelColor.fromPackedRgba8888(0x01020300), created(source.colorAt(pixelPosition(0, 0))))
        assertEquals(-1, source.copyPackedRgba8888()[1])
        assertInstanceOf(
            DomainValueRejection.PixelPositionOutsideCanvas::class.java,
            rejected(source.colorAt(pixelPosition(2, 0))),
        )
    }

    @Test
    fun `legacy source rejects size mismatch and import branches preserve typed values`() {
        val rejection =
            rejected(
                LegacyRgbaSource.createPackedRgba8888(ID, Revision.initial(), canvasSize(2, 1), intArrayOf(0)),
            )
        assertEquals(DomainValueRejection.LegacyRgbaSourceSizeMismatch(2, 1), rejection)
        val source =
            created(
                LegacyRgbaSource.createPackedRgba8888(ID, Revision.initial(), canvasSize(1, 1), intArrayOf(0)),
            )
        assertEquals(source, (DocumentImportSource.Legacy(source)).source)
    }

    private companion object {
        val ID = created(DocumentId.create("a".repeat(32)))
    }
}
