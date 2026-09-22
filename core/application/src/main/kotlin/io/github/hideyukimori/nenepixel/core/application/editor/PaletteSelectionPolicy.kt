package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.DocumentCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.ReplacePaletteCommand
import io.github.hideyukimori.nenepixel.core.application.document.transition.PaletteTransition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal object PaletteSelectionPolicy {
    fun afterApplied(
        current: PaletteIndex,
        definition: PaletteDefinition,
        command: DocumentCommand,
        transition: PaletteTransition,
    ): PaletteIndex? =
        when {
            command is ReplacePaletteCommand -> {
                when (val mapped = command.remap.destinationAt(current)) {
                    is DomainValueResult.Created -> {
                        mapped.value
                    }

                    is DomainValueResult.Rejected -> {
                        error("Admitted palette replacement lost selection: ${mapped.rejection}")
                    }
                }
            }

            transition is PaletteTransition.Changed -> {
                restored(definition, current)
            }

            else -> {
                null
            }
        }

    private fun restored(
        definition: PaletteDefinition,
        index: PaletteIndex,
    ): PaletteIndex =
        when (definition.palette.entryAt(index)) {
            is DomainValueResult.Created -> index
            is DomainValueResult.Rejected -> definition.defaultIndex
        }
}
