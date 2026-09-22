package io.github.hideyukimori.nenepixel.core.pixelengine.palette

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.CANVAS_EDGE
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.CANVAS_PIXELS
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.FULL_PALETTE
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.allocatedBytes
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.palette
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.remap
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationTestValues.snapshot
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.math.ceil

internal class PaletteRemapApplicationMeasurementTest {
    @Test
    fun `record host cost of identity and many to one palette remaps`() {
        assumeTrue(
            java.lang.Boolean.getBoolean(OPT_IN_PROPERTY),
            "Palette remap timing is an explicit, finite-budget diagnostic.",
        )
        workloads().forEach(::measure)
    }

    private fun measure(workload: RemapWorkload) {
        repeat(WARMUPS) { applyPaletteRemap(workload.snapshot, workload.remap) }
        val samples = List(SAMPLES) { timedNanos(workload) }
        val allocated = allocatedBytes { applyPaletteRemap(workload.snapshot, workload.remap) }
        println(
            "PALETTE_REMAP_MEASUREMENT,workload=${workload.name},pixels=$CANVAS_PIXELS,samples=$SAMPLES," +
                "p50_nanos=${nearestRank(samples, HALF)},p95_nanos=${nearestRank(samples, UPPER)}," +
                "max_nanos=${samples.max()},allocated_bytes=$allocated," +
                "allocated_bytes_per_pixel=${allocated.toDouble() / CANVAS_PIXELS}",
        )
    }

    private fun workloads(): List<RemapWorkload> {
        val source = palette(FULL_PALETTE)
        val target = palette(2)
        val gradient = raster { position -> position % FULL_PALETTE }
        return listOf(
            RemapWorkload("identity_full_palette", gradient, remap(source, source, List(FULL_PALETTE) { it })),
            RemapWorkload(
                "many_to_one_dense",
                raster { position -> position % (FULL_PALETTE - 2) + 2 },
                remap(source, target, List(FULL_PALETTE) { 0 }),
            ),
            RemapWorkload("many_to_one_alternating", gradient, remap(source, target, List(FULL_PALETTE) { it % 2 })),
        )
    }

    private fun raster(packedIndexAt: (Int) -> Int): PixelSnapshot =
        snapshot(canvas(CANVAS_EDGE, CANVAS_EDGE), packedIndexAt)

    private fun timedNanos(workload: RemapWorkload): Long {
        val startedAt = System.nanoTime()
        val result = applyPaletteRemap(workload.snapshot, workload.remap)
        val elapsed = System.nanoTime() - startedAt
        check(result !is PaletteRemapApplicationResult.Rejected) { "${workload.name} was rejected." }
        return elapsed
    }

    private fun nearestRank(
        samples: List<Long>,
        percentile: Double,
    ): Long = samples.sorted()[ceil(samples.size * percentile).toInt() - 1]

    private companion object {
        const val WARMUPS: Int = 8
        const val SAMPLES: Int = 20
        const val HALF: Double = 0.50
        const val UPPER: Double = 0.95
        const val OPT_IN_PROPERTY: String = "nene.paletteRemapMeasurement"
    }
}

private class RemapWorkload(
    val name: String,
    val snapshot: PixelSnapshot,
    val remap: PaletteRemap,
)
