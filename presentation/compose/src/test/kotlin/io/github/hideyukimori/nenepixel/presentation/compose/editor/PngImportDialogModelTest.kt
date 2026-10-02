package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues
import io.github.hideyukimori.nenepixel.presentation.compose.R
import io.github.hideyukimori.nenepixel.presentation.compose.requiredValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ADR 0033 "Controls": the third form, opening the PNG as a new work, states what it does when it can be chosen
 * and only its reason when it cannot, and does not depend on the layer limit.
 */
internal class PngImportDialogModelTest {
    @Test
    fun `a small two colour PNG can open as a new work and says it closes the current document`() {
        val model = modelFor(ImportRaster.create(2, 1, intArrayOf(OPAQUE_RED, OPAQUE_BLUE)).requiredValue())

        assertTrue(model.newWork.enabled)
        assertEquals(listOf(PngImportLine(R.string.png_import_new_work_note)), model.newWork.lines)
    }

    @Test
    fun `a PNG wider than the canvas limit cannot open as a new work but can still be appended`() {
        val pixels = IntArray(WIDE) { if (it == 0) OPAQUE_RED else TRANSPARENT }
        val model = modelFor(ImportRaster.create(WIDE, 1, pixels).requiredValue())

        assertFalse(model.newWork.enabled)
        assertEquals(listOf(PngImportLine(R.string.png_import_new_work_too_large)), model.newWork.lines)
        assertTrue(model.append.enabled)
    }

    @Test
    fun `a PNG with more colours than a palette holds cannot open as a new work`() {
        val pixels = IntArray(MANY_COLOURS_WIDTH * 2) { OPAQUE_ALPHA or (it shl ALPHA_BITS) }
        val model = modelFor(ImportRaster.create(MANY_COLOURS_WIDTH, 2, pixels).requiredValue())

        assertFalse(model.newWork.enabled)
        assertEquals(listOf(PngImportLine(R.string.png_import_new_work_too_many_colors)), model.newWork.lines)
    }

    @Test
    fun `a fully transparent PNG cannot open as a new work because nothing would be imported`() {
        val model = modelFor(ImportRaster.create(2, 2, IntArray(4) { TRANSPARENT }).requiredValue())

        assertFalse(model.newWork.enabled)
        assertEquals(listOf(PngImportLine(R.string.png_import_nothing)), model.newWork.lines)
    }

    @Test
    fun `at sixteen layers only the layer forms are unavailable`() {
        val model =
            modelFor(ImportRaster.create(2, 1, intArrayOf(OPAQUE_RED, OPAQUE_BLUE)).requiredValue(), MAX_LAYERS)

        assertFalse(model.append.enabled)
        assertFalse(model.convert.enabled)
        assertTrue(model.newWork.enabled)
        assertEquals(listOf(PngImportLine(R.string.png_import_new_work_note)), model.newWork.lines)
    }

    /** Picks [raster] through the real workflow and builds the dialog for a document of [layerCount] layers. */
    private fun modelFor(
        raster: ImportRaster,
        layerCount: Int = 1,
    ): PngImportDialogModel {
        val fixture = PresentationTestValues.fixture()
        pickPngImport(fixture, raster)
        val pending = checkNotNull(fixture.runtime.state.workspaceState.pendingImport)
        return pngImportDialogModel(pending, layerCount)
    }

    private companion object {
        const val MAX_LAYERS: Int = 16
        const val WIDE: Int = 257
        const val MANY_COLOURS_WIDTH: Int = 129
        const val ALPHA_BITS: Int = 8
        const val OPAQUE_ALPHA: Int = 0xFF
        const val OPAQUE_RED: Int = 0xFF0000FF.toInt()
        const val OPAQUE_BLUE: Int = 0x0000FFFF
        const val TRANSPARENT: Int = 0
    }
}
