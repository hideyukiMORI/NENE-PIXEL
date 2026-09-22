package io.github.hideyukimori.nenepixel.core.pixelengine.palette

import com.sun.management.ThreadMXBean
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatch
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchApplicationResult
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapTestValues.definition
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapTestValues.index
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapTestValues.value
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.fail
import java.lang.management.ManagementFactory

internal object PaletteRemapApplicationTestValues {
    const val FULL_PALETTE: Int = 256
    const val CANVAS_EDGE: Int = 256
    const val CANVAS_PIXELS: Int = CANVAS_EDGE * CANVAS_EDGE

    fun palette(entryCount: Int): PaletteDefinition = definition(List(entryCount) { PixelColor.blank })

    fun remap(
        source: PaletteDefinition,
        target: PaletteDefinition,
        destinations: List<Int>,
    ): PaletteRemap = value(PaletteRemap.create(source, target, destinations.map(::index)))

    fun snapshot(
        size: CanvasSize,
        packedIndexAt: (Int) -> Int,
    ): PixelSnapshot =
        value(
            PixelSnapshot.createPackedIndices(
                size,
                Revision.initial(),
                ByteArray(size.pixelCount.toInt()) { position -> packedIndexAt(position).toByte() },
            ),
        )

    fun changedPatch(result: PaletteRemapApplicationResult): PixelPatch =
        assertInstanceOf(PaletteRemapApplicationResult.Changed::class.java, result).patch

    fun applied(result: PixelPatchApplicationResult): PixelSnapshot =
        when (result) {
            is PixelPatchApplicationResult.Applied -> result.snapshot
            is PixelPatchApplicationResult.Rejected -> fail("Patch rejected: ${result.rejection}")
        }

    fun allocatedBytes(block: () -> Unit): Long {
        val counter = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        val enabled = counter != null && counter.isThreadAllocatedMemoryEnabled
        assumeTrue(enabled, "Thread allocation counters are unavailable on this JVM.")
        var allocated = 0L
        counter?.let {
            val start = it.currentThreadAllocatedBytes
            block()
            allocated = it.currentThreadAllocatedBytes - start
        }
        return allocated
    }
}
