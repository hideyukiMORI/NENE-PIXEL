package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.workspace.importing.PendingRasterImport
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.RasterImportOption
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.LayerImportResult
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportPlanner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.fail

private const val OPAQUE_RED = 0xFF0000FF.toInt()
private const val OPAQUE_BLUE = 0x0000FFFF
private const val TRANSPARENT = 0

/** A 2x2 raster with two colours, one of them not in the fixture palette, and one transparent pixel. */
internal fun importRaster(): ImportRaster =
    when (val result = ImportRaster.create(2, 2, intArrayOf(OPAQUE_RED, OPAQUE_BLUE, TRANSPARENT, OPAQUE_RED))) {
        is DomainValueResult.Created -> result.value
        is DomainValueResult.Rejected -> fail("Import raster fixture was rejected: ${result.rejection}")
    }

/** Asserts that [pending] holds the facts and both layer plans of [raster] against [canvas] and [definition]. */
internal fun assertPlannedFor(
    pending: PendingRasterImport,
    raster: ImportRaster,
    canvas: CanvasSize,
    definition: PaletteDefinition,
) {
    assertEquals(raster.width, pending.facts.width)
    assertEquals(raster.height, pending.facts.height)
    assertEquals(RasterImportPlanner.colorCount(raster), pending.facts.colorCount)
    assertSamePlan(RasterImportPlanner.appending(raster, canvas, definition), pending.appending)
    assertSamePlan(RasterImportPlanner.converting(raster, canvas, definition), pending.converting)
}

private fun assertSamePlan(
    expected: LayerImportResult,
    actual: RasterImportOption,
) {
    val expectedPlan = assertInstanceOf(LayerImportResult.Planned::class.java, expected).plan
    val actualPlan = assertInstanceOf(RasterImportOption.Available::class.java, actual).plan
    assertEquals(expectedPlan.source, actualPlan.source)
    assertEquals(expectedPlan.target, actualPlan.target)
    assertEquals(expectedPlan.snapshot, actualPlan.snapshot)
    assertEquals(expectedPlan.loss, actualPlan.loss)
}
