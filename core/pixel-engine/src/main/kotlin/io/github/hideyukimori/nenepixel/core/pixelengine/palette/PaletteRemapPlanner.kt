package io.github.hideyukimori.nenepixel.core.pixelengine.palette

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteLimits
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

public object PaletteRemapPlanner {
    public fun byNumber(
        source: PaletteDefinition,
        target: PaletteDefinition,
    ): PaletteRemapResult = explicit(source, target, source.palette.entries().map { it.index })

    public fun nearest(
        source: PaletteDefinition,
        target: PaletteDefinition,
    ): PaletteRemapResult {
        val targets = target.palette.entries()
        val destinations = source.palette.entries().map { NearestPaletteEntry.find(it.color, targets) }
        return explicit(source, target, destinations)
    }

    public fun explicit(
        source: PaletteDefinition,
        target: PaletteDefinition,
        destinations: List<PaletteIndex>,
    ): PaletteRemapResult = planned(PaletteRemap.create(source, target, destinations))

    public fun reorder(
        source: PaletteDefinition,
        newOrder: List<PaletteIndex>,
    ): PaletteRemapResult {
        val count = source.palette.entryCount
        if (newOrder.size != count) return rejected(PaletteRemapRejection.OrderSizeMismatch(count, newOrder.size))
        val owned = newOrder.toList()
        val seen = BooleanArray(count)
        val invalid =
            owned.firstNotNullOfOrNull { slot ->
                when {
                    slot.value >= count -> {
                        PaletteRemapRejection.OrderIndexOutsidePalette(slot)
                    }

                    seen[slot.value] -> {
                        PaletteRemapRejection.RepeatedOrderIndex(slot)
                    }

                    else -> {
                        seen[slot.value] = true
                        null
                    }
                }
            }
        return if (invalid != null) rejected(invalid) else ordered(source, owned)
    }

    public fun remove(
        source: PaletteDefinition,
        removed: PaletteIndex,
        replacement: PaletteIndex = source.defaultIndex,
    ): PaletteRemapResult =
        when {
            removed.value >= source.palette.entryCount -> {
                rejected(PaletteRemapRejection.RemovedIndexOutsidePalette(removed))
            }

            replacement.value >= source.palette.entryCount -> {
                rejected(PaletteRemapRejection.ReplacementIndexOutsidePalette(replacement))
            }

            removed == replacement -> {
                rejected(PaletteRemapRejection.ReplacementIsRemovedIndex)
            }

            source.palette.entryCount <= PaletteLimits.MIN_DEFINITION_ENTRY_COUNT -> {
                rejected(PaletteRemapRejection.BelowDefinitionMinimum)
            }

            else -> {
                removed(source, removed, replacement)
            }
        }

    private fun ordered(
        source: PaletteDefinition,
        order: List<PaletteIndex>,
    ): PaletteRemapResult {
        val entries = source.palette.entries()
        val destinations = MutableList(entries.size) { PaletteIndex.first }
        val colors =
            order.mapIndexed { newIndex, oldIndex ->
                destinations[oldIndex.value] = index(newIndex)
                entries[oldIndex.value].color
            }
        return constructed(source, colors, destinations)
    }

    private fun removed(
        source: PaletteDefinition,
        removed: PaletteIndex,
        replacement: PaletteIndex,
    ): PaletteRemapResult {
        val entries = source.palette.entries()
        val colors = entries.filter { it.index != removed }.map { it.color }
        val destinations =
            entries.map { entry ->
                val survivor = if (entry.index == removed) replacement else entry.index
                index(if (survivor.value > removed.value) survivor.value - 1 else survivor.value)
            }
        return constructed(source, colors, destinations)
    }

    private fun constructed(
        source: PaletteDefinition,
        colors: List<PixelColor>,
        destinations: List<PaletteIndex>,
    ): PaletteRemapResult {
        val palette = value(Palette.create(colors))
        val target = value(PaletteDefinition.create(palette, destinations[source.defaultIndex.value]))
        return explicit(source, target, destinations)
    }

    private fun index(number: Int): PaletteIndex = value(PaletteIndex.create(number))

    private fun <T> value(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Validated remap construction rejected: ${result.rejection}")
        }
}
