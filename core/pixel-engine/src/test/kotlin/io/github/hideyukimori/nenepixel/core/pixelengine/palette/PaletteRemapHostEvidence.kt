package io.github.hideyukimori.nenepixel.core.pixelengine.palette

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapTestValues.definition
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapTestValues.index
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapTestValues.planned

private const val WARMUPS: Int = 5
private const val SAMPLES: Int = 20
private const val ANOMALY_NANOS: Long = 1_000_000_000L

internal fun main() {
    val inputs = RemapEvidenceInputs()
    inputs.verify()
    println("schema,palette-remap-host-v1")
    println("java_version,${System.getProperty("java.version")}")
    println("java_vm,${System.getProperty("java.vm.name")}")
    println("os,${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
    println("warmups,$WARMUPS")
    println("samples_per_group,$SAMPLES")
    println("group,sample,latency_nanos")
    System.out.flush()
    val observations = inputs.groups.flatMap(::measure)
    inputs.verify()
    observations.groupBy(RemapObservation::group).forEach { (group, rows) ->
        println("summary,$group,${rows.minOf(RemapObservation::nanos)},${rows.maxOf(RemapObservation::nanos)}")
    }
    System.out.flush()
}

private fun measure(group: RemapEvidenceGroup): List<RemapObservation> {
    repeat(WARMUPS) { group.verifyFacts(group.operation()) }
    return List(SAMPLES) { sample ->
        val start = System.nanoTime()
        val result = group.operation()
        val nanos = System.nanoTime() - start
        println("${group.name},$sample,$nanos")
        System.out.flush()
        group.verifyFacts(result)
        check(nanos <= ANOMALY_NANOS) { "${group.name} sample $sample exceeded one second" }
        RemapObservation(group.name, nanos)
    }
}

private class RemapEvidenceInputs {
    private val source = definition(List(256) { grayscale(it, 255) }, 127)
    private val target = definition(List(256) { grayscale(it, 254) }, 255)
    private val reversed = (255 downTo 0).map(::index)
    private val removedIndex = index(127)
    private val replacementIndex = index(255)
    val groups =
        listOf(
            RemapEvidenceGroup("number_256", 256, 255) { PaletteRemapPlanner.byNumber(source, target) },
            RemapEvidenceGroup("nearest_256", 256, 255) { PaletteRemapPlanner.nearest(source, target) },
            RemapEvidenceGroup("reorder_256", 256, 128) { PaletteRemapPlanner.reorder(source, reversed) },
            RemapEvidenceGroup("remove_256", 255, 254) {
                PaletteRemapPlanner.remove(source, removedIndex, replacementIndex)
            },
        )

    fun verify() {
        verifyMappings()
        verifyColors()
    }

    private fun verifyMappings() {
        val expected =
            listOf(
                (0..255).toList(),
                (0..255).map { if (it <= 127) it else minOf(it + 1, 255) },
                (255 downTo 0).toList(),
                (0..255).map {
                    when {
                        it == 127 -> 254
                        it > 127 -> it - 1
                        else -> it
                    }
                },
            )
        groups.zip(expected).forEach { (group, destinations) ->
            val result = group.operation()
            group.verifyFacts(result)
            check(planned(result).source == source)
            check(planned(result).destinations().map { it.value } == destinations)
        }
    }

    private fun verifyColors() {
        val ordered = planned(groups[2].operation())
        check(
            ordered.target.palette
                .entries()
                .map { it.color } ==
                source.palette
                    .entries()
                    .reversed()
                    .map { it.color },
        )
        val removed = planned(groups[3].operation())
        check(
            removed.target.palette
                .entries()
                .map { it.color } ==
                source.palette
                    .entries()
                    .filter {
                        it.index.value != 127
                    }.map { it.color },
        )
    }

    private fun grayscale(
        value: Int,
        alpha: Int,
    ): PixelColor = PixelColor.fromPackedRgba8888((value shl 24) or (value shl 16) or (value shl 8) or alpha)
}

private class RemapEvidenceGroup(
    val name: String,
    val count: Int,
    val default: Int,
    val operation: () -> PaletteRemapResult,
) {
    fun verifyFacts(result: PaletteRemapResult) {
        check(result is PaletteRemapResult.Planned)
        check(result.remap.source.palette.entryCount == 256)
        check(result.remap.target.palette.entryCount == count && result.remap.target.defaultIndex.value == default)
    }
}

private data class RemapObservation(
    val group: String,
    val nanos: Long,
)
