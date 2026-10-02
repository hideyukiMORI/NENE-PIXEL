package io.github.hideyukimori.nenepixel.core.domain.importing

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.rejected
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class LayerImportLossTest {
    @Test
    fun `zero counts are created`() {
        val loss = created(LayerImportLoss.create(0, 0))

        assertEquals(0, loss.nearestColorCount)
        assertEquals(0, loss.droppedPixelCount)
    }

    @Test
    fun `positive counts are read back and compared by value`() {
        val loss = created(LayerImportLoss.create(3, 7))

        assertEquals(3, loss.nearestColorCount)
        assertEquals(7, loss.droppedPixelCount)
        assertEquals(created(LayerImportLoss.create(3, 7)), loss)
    }

    @Test
    fun `negative nearest colour count is rejected`() {
        assertEquals(
            DomainValueRejection.NegativeImportCount(-1),
            rejected(LayerImportLoss.create(-1, 0)),
        )
    }

    @Test
    fun `negative dropped pixel count is rejected`() {
        assertEquals(
            DomainValueRejection.NegativeImportCount(-2),
            rejected(LayerImportLoss.create(0, -2)),
        )
    }
}
