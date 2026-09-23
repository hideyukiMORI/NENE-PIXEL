package io.github.hideyukimori.nenepixel.core.application.workspace.palette

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapPlanner
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapResult

/**
 * An imported palette waiting to replace the draft. `assignments` maps a draft (source) slot to an import (target)
 * slot; it is kept across mode changes and consulted only by the modes that need it.
 */
public class PendingPaletteImport internal constructor(
    public val target: PaletteDefinition,
    public val mode: PaletteImportMode,
    public val assignments: Map<PaletteIndex, PaletteIndex>,
) {
    internal fun withMode(changed: PaletteImportMode): PendingPaletteImport =
        PendingPaletteImport(target, changed, assignments)

    internal fun assigned(
        source: PaletteIndex,
        destination: PaletteIndex,
    ): PendingPaletteImport = PendingPaletteImport(target, mode, assignments + (source to destination))

    /** Resolves one destination per slot of `source`, or lists every source slot that still needs an assignment. */
    internal fun resolve(source: PaletteDefinition): PaletteImportResolution =
        when (mode) {
            PaletteImportMode.ByNumber -> {
                resolved(source) { index ->
                    if (index.value < target.palette.entryCount) index else assignments[index]
                }
            }

            PaletteImportMode.Nearest -> {
                nearest(source)
            }

            PaletteImportMode.Explicit -> {
                resolved(source) { index -> assignments[index] }
            }
        }

    private fun resolved(
        source: PaletteDefinition,
        destinationOf: (PaletteIndex) -> PaletteIndex?,
    ): PaletteImportResolution {
        val sources = source.palette.entries().map { it.index }
        val destinations = sources.map(destinationOf)
        val unresolved = sources.filterIndexed { position, _ -> destinations[position] == null }
        return if (unresolved.isEmpty()) {
            PaletteImportResolution.Resolved(destinations.filterNotNull())
        } else {
            PaletteImportResolution.Unresolved(unresolved)
        }
    }

    private fun nearest(source: PaletteDefinition): PaletteImportResolution =
        when (val remap = PaletteRemapPlanner.nearest(source, target)) {
            is PaletteRemapResult.Planned -> PaletteImportResolution.Resolved(remap.remap.destinations())
            is PaletteRemapResult.Rejected -> error("Nearest import remap is invalid: ${remap.rejection}")
        }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is PendingPaletteImport &&
                    target == other.target &&
                    mode == other.mode &&
                    assignments == other.assignments
            )

    override fun hashCode(): Int =
        HASH_MULTIPLIER * (HASH_MULTIPLIER * target.hashCode() + mode.hashCode()) +
            assignments.hashCode()

    override fun toString(): String =
        "PendingPaletteImport(targetCount=${target.palette.entryCount}, mode=$mode, " +
            "assignments=$assignments)"

    private companion object {
        const val HASH_MULTIPLIER: Int = 31
    }
}

/** The resolution of a pending import against the current draft. */
internal sealed interface PaletteImportResolution {
    data class Resolved(
        val destinations: List<PaletteIndex>,
    ) : PaletteImportResolution

    data class Unresolved(
        val sources: List<PaletteIndex>,
    ) : PaletteImportResolution
}
