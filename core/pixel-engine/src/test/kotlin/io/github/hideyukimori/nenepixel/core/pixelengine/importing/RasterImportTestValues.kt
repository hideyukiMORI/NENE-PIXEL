package io.github.hideyukimori.nenepixel.core.pixelengine.importing

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.core.domain.importing.LayerImportPlan
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.fail

internal object RasterImportTestValues {
    const val BLACK: Int = 0x000000ff
    const val WHITE: Int = 0xffffffff.toInt()
    const val RED: Int = 0xff0000ff.toInt()
    const val HALF_RED: Int = 0xff000080.toInt()
    const val GREEN: Int = 0x00ff00ff
    const val BLUE: Int = 0x0000ffff
    const val CLEAR: Int = 0
    const val HIDDEN_CLEAR: Int = 0x12345600

    fun raster(
        width: Int,
        height: Int,
        vararg packed: Int,
    ): ImportRaster = ImportRaster.create(width, height, packed).value()

    fun palette(
        vararg packed: Int,
        defaultIndex: Int = 0,
    ): PaletteDefinition =
        PaletteDefinition
            .create(
                Palette.create(packed.map(PixelColor::fromPackedRgba8888)).value(),
                PaletteIndex.create(defaultIndex).value(),
            ).value()

    /** Opaque colours (0, 0, blue, 255) for blue in 0 until [count]. */
    fun blues(count: Int): IntArray = IntArray(count) { (it shl BLUE_SHIFT) or OPAQUE }

    fun color(
        red: Int,
        green: Int,
        blue: Int,
    ): Int = (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or (blue shl BLUE_SHIFT) or OPAQUE

    fun planned(result: LayerImportResult): LayerImportPlan =
        assertInstanceOf(LayerImportResult.Planned::class.java, result).plan

    fun indices(plan: LayerImportPlan): List<Int> = plan.snapshot.copyPackedIndices().map { it.toInt() and U8 }

    fun coverage(plan: LayerImportPlan): List<Int> {
        val bytes = plan.snapshot.copyCoverage()
        return List(indices(plan).size) { (bytes[it / BYTE_BITS].toInt() ushr (it % BYTE_BITS)) and 1 }
    }

    fun targetColors(plan: LayerImportPlan): List<Int> =
        plan.target.palette
            .entries()
            .map { it.color.toPackedRgba8888() }

    fun <T> DomainValueResult<T>.value(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> fail("Value rejected: $rejection")
        }

    private const val OPAQUE: Int = 0xff
    private const val U8: Int = 0xff
    private const val BYTE_BITS: Int = 8
    private const val RED_SHIFT: Int = 24
    private const val GREEN_SHIFT: Int = 16
    private const val BLUE_SHIFT: Int = 8
}
