package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.assertRejectedWithoutEffect
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.stateWithIds
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layer
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layeredState
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerLimits
import org.junit.jupiter.api.Test

/** Issue #208: bounded insertion and source admission reject atomically before commit/history. */
internal class DuplicateLayerRejectionTest {
    @Test
    fun `sixteen layers reject duplication without changing source history or retention`() {
        val original = layeredState((1..LayerLimits.MAX_LAYERS).map { layer(it, "") })
        val gateway = CommandGateway.create(original)
        assertRejectedWithoutEffect(gateway, duplicate(gateway), RejectionReason.LayerLimitReached)
    }

    @Test
    fun `maximum layer ID and revision cannot overflow through duplication`() {
        val ids = CommandGateway.create(stateWithIds(1, Int.MAX_VALUE))
        assertRejectedWithoutEffect(ids, duplicate(ids), RejectionReason.LayerIdOverflow)
        val revisions = CommandGateway.create(layeredState(listOf(layer(1, "")), revision(Long.MAX_VALUE)))
        assertRejectedWithoutEffect(revisions, duplicate(revisions), RejectionReason.RevisionOverflow)
    }

    @Test
    fun `stale or foreign source admission cannot duplicate an existing layer`() {
        val gateway = CommandGateway.create(stateWithIds(1, 2))
        val stale = duplicate(gateway)
        applied(gateway.execute(AddLayerCommand.create(gateway.captureSource(), layerId(2))))
        assertRejectedWithoutEffect(gateway, stale, RejectionReason.SourceHistoryMismatch)
        val foreign = duplicate(CommandGateway.create(stateWithIds(1, 2)))
        assertRejectedWithoutEffect(gateway, foreign, RejectionReason.SourceOwnerMismatch)
    }

    private fun duplicate(gateway: CommandGateway): DuplicateLayerCommand =
        DuplicateLayerCommand.create(gateway.captureSource(), layerId(1))
}
