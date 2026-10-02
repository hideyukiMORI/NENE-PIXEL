package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.blackIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.definition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.snapshot
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.smallCanvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.value
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.importing.LayerImportLoss
import io.github.hideyukimori.nenepixel.core.domain.importing.LayerImportPlan
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

/** Hand-built [LayerImportPlan] values against [defaultDefinition] (black, red, green; default black). */
internal object ImportLayerTestValues {
    /** The appended slot of [appendedDefinition]. */
    val appendedIndex: PaletteIndex = paletteIndex(3)

    /** [defaultDefinition] with one colour appended at [appendedIndex]. */
    val appendedDefinition: PaletteDefinition = definition(blackIndex, black, red, green, red)

    /** Adding form: the 2x1 layer uses the appended slot and red; the palette gains one colour. */
    fun addingPlan(): LayerImportPlan =
        plan(defaultDefinition, appendedDefinition, smallCanvas, listOf(appendedIndex, redIndex))

    /** Converting form: the 2x1 layer uses existing slots only; the palette is unchanged. */
    fun convertingPlan(): LayerImportPlan =
        plan(defaultDefinition, defaultDefinition, smallCanvas, listOf(redIndex, blackIndex))

    fun plan(
        source: PaletteDefinition,
        target: PaletteDefinition,
        canvas: CanvasSize,
        indices: List<PaletteIndex>,
    ): LayerImportPlan {
        val loss = LayerImportLoss.create(nearestColorCount = 0, droppedPixelCount = 0).value()
        return LayerImportPlan.create(source, target, snapshot(canvas, indices), loss).value()
    }

    fun import(
        gateway: CommandGateway,
        above: Int,
        plan: LayerImportPlan,
    ): ImportLayerCommand = ImportLayerCommand.create(gateway.captureSource(), layerId(above), plan)
}
