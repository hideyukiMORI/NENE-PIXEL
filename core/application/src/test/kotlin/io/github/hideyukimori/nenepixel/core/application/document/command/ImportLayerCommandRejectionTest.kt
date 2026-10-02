package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.command.ImportLayerTestValues.addingPlan
import io.github.hideyukimori.nenepixel.core.application.document.command.ImportLayerTestValues.import
import io.github.hideyukimori.nenepixel.core.application.document.command.ImportLayerTestValues.plan
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.assertRejectedWithoutEffect
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.missing
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.stateWithIds
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.blackIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.definition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.smallCanvas
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerLimits
import org.junit.jupiter.api.Test

/** [ImportLayerCommand] refusals leave document, revision, history and retention untouched (ADR 0033). */
internal class ImportLayerCommandRejectionTest {
    @Test
    fun `a plan for another palette definition is refused before anything else`() {
        val gateway = CommandGateway.create(stateWithIds(1, 2))
        val other = definition(blackIndex, black, red)
        val stale = plan(other, other, smallCanvas, listOf(redIndex, blackIndex))
        val command = ImportLayerCommand.create(gateway.captureSource(), missing(), stale)

        assertRejectedWithoutEffect(gateway, command, RejectionReason.PaletteSourceMismatch(other, defaultDefinition))
    }

    @Test
    fun `a plan for another canvas size is refused`() {
        val gateway = CommandGateway.create(stateWithIds(1, 2))
        val otherCanvas = canvas(1, 1)
        val stale = plan(defaultDefinition, defaultDefinition, otherCanvas, listOf(redIndex))

        assertRejectedWithoutEffect(
            gateway,
            import(gateway, above = 1, stale),
            RejectionReason.CanvasMismatch(otherCanvas, smallCanvas),
        )
    }

    @Test
    fun `a missing target layer is refused`() {
        val gateway = CommandGateway.create(stateWithIds(1, 2))
        val command = ImportLayerCommand.create(gateway.captureSource(), missing(), addingPlan())

        assertRejectedWithoutEffect(gateway, command, RejectionReason.LayerNotFound(missing()))
    }

    @Test
    fun `a document at the layer limit refuses the import`() {
        val gateway = CommandGateway.create(stateWithIds(*IntArray(LayerLimits.MAX_LAYERS) { it + 1 }))

        val command = import(gateway, above = 1, addingPlan())

        assertRejectedWithoutEffect(gateway, command, RejectionReason.LayerLimitReached)
    }
}
