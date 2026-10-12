package io.github.hideyukimori.nenepixel.core.application.render

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.TransferPixelsCommand
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.drawnRuntime
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelPlacement
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransfer
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransferMode
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class SelectionPreviewCompositeTest {
    @Test
    fun `application projection equals real one-command Move Copy result over other layers`() {
        PixelTransferMode.entries.forEach { mode ->
            val runtime = drawnRuntime()
            val document = runtime.state.documentState
            val source = value(PixelRegion.create(document.size, position(0, 0), canvas(2, 1)))
            val transfer =
                value(PixelTransfer.create(document.size, source, PixelPlacement.create(position(1, 1)), mode))
            val prepared = SelectionPreviewComposite.prepare(document, LayerId.first(), source)
            val image = prepared.render(transfer)
            val before = DocumentComposite.render(document).copyPackedRgba8888()
            assertInstanceOf(
                CommandResult.Applied::class.java,
                runtime.execute(TransferPixelsCommand.create(runtime.captureSource(), LayerId.first(), transfer)),
            )
            val expected = DocumentComposite.render(runtime.state.documentState).copyPackedRgba8888()
            val actual = before.copyOf()
            image.forEachPosition { pixel -> actual[pixel.y.value * 3 + pixel.x.value] = image.packedRgba8888At(pixel) }
            assertEquals(expected.toList(), actual.toList())
        }
    }

    private fun <T> value(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid fixture: ${result.rejection}")
        }
}
