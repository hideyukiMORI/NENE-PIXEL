package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.rejected
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.DocumentIdentity
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.blackIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.cellAt
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerIndexChanges
import io.github.hideyukimori.nenepixel.core.application.document.transition.PaletteTransition
import io.github.hideyukimori.nenepixel.core.application.document.transition.singleFrameSnapshot
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelPlacement
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransfer
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransferMode
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class TransferPixelsCommandTest {
    @Test
    fun `sparse move returns one ordinary patch with narrow invalidation and exact undo redo`() {
        val size = canvas(8, 8)
        val initial = state(size, List(64) { if (it == 0) redIndex else blackIndex })
        val gateway = CommandGateway.create(initial)
        val change = applied(gateway.execute(command(gateway, transfer(size))))
        val moved = gateway.runtimeState.documentState

        assertInstanceOf(LayerIndexChanges.Sparse::class.java, change.layerChanges.single().changes)
        assertEquals(PaletteTransition.Unchanged, change.paletteTransition)
        assertEquals(
            2,
            change.layerChanges
                .single()
                .changes.changeCount,
        )
        assertEquals(44L, change.retainedByteCount)
        assertEquals(44L, gateway.runtimeState.retainedHistoryByteCount)
        assertEquals(canvas(2, 1), change.renderInvalidation.size)
        assertEquals(
            PixelCell.Empty,
            cellAt(
                moved.singleFrameSnapshot(),
                position(0, 0),
            ),
        )
        assertEquals(
            PixelCell.Covered(redIndex),
            cellAt(
                moved.singleFrameSnapshot(),
                position(1, 0),
            ),
        )
        applied(gateway.execute(UndoCommand.create(moved.id, moved.revision)))
        assertEquals(initial, gateway.runtimeState.documentState)
        applied(gateway.execute(RedoCommand.create(initial.id, initial.revision)))
        assertEquals(moved, gateway.runtimeState.documentState)
        assertEquals(1, gateway.runtimeState.historyEntryCount)
    }

    @Test
    fun `overlap chooses canonical dense history and deterministic replay`() {
        val size = canvas(4, 1)
        val initial = state(size, listOf(redIndex, blackIndex, redIndex, blackIndex))
        val source = value(PixelRegion.create(size, position(0, 0), canvas(3, 1)))
        val transfer =
            value(PixelTransfer.create(size, source, PixelPlacement.create(position(1, 0)), PixelTransferMode.Move))
        val first = CommandGateway.create(initial)
        val second = CommandGateway.create(initial)
        val change = applied(first.execute(command(first, transfer)))
        val replay = applied(second.execute(command(second, transfer)))

        assertInstanceOf(LayerIndexChanges.Dense::class.java, change.layerChanges.single().changes)
        assertEquals(42L, change.retainedByteCount)
        assertEquals(42L, first.runtimeState.retainedHistoryByteCount)
        assertEquals(size, change.renderInvalidation.size)
        assertEquals(change, replay)
        val moved = first.runtimeState.documentState
        applied(first.execute(UndoCommand.create(moved.id, moved.revision)))
        assertEquals(initial, first.runtimeState.documentState)
        applied(first.execute(RedoCommand.create(initial.id, initial.revision)))
        assertEquals(moved, first.runtimeState.documentState)
    }

    @Test
    fun `direct zero displacement is a no-op that preserves redo and history position`() {
        val size = canvas(8, 8)
        val initial = state(size, List(64) { if (it == 0) redIndex else blackIndex })
        val gateway = CommandGateway.create(initial)
        applied(gateway.execute(command(gateway, transfer(size))))
        val moved = gateway.runtimeState.documentState
        applied(gateway.execute(UndoCommand.create(moved.id, moved.revision)))
        val before = gateway.runtimeState
        val source = value(PixelRegion.create(size, position(0, 0), canvas(1, 1)))
        val noop =
            value(PixelTransfer.create(size, source, PixelPlacement.create(source.origin), PixelTransferMode.Copy))

        assertEquals(RejectionReason.NoEffectiveChange, rejected(gateway.execute(command(gateway, noop))))
        assertEquals(before, gateway.runtimeState)
        applied(gateway.execute(RedoCommand.create(initial.id, initial.revision)))
        assertEquals(moved, gateway.runtimeState.documentState)
    }

    @Test
    fun `foreign owner and stale history reject before any transition`() {
        val size = canvas(8, 8)
        val initial = state(size, List(64) { if (it == 0) redIndex else blackIndex })
        val gateway = CommandGateway.create(initial)
        val foreign = CommandGateway.create(initial)
        val planned = transfer(size)
        val stale = command(gateway, planned)
        assertEquals(RejectionReason.SourceOwnerMismatch, rejected(gateway.execute(command(foreign, planned))))
        applied(gateway.execute(command(gateway, planned)))
        val before = gateway.runtimeState

        assertEquals(RejectionReason.SourceHistoryMismatch, rejected(gateway.execute(stale)))
        assertEquals(before, gateway.runtimeState)
    }

    @Test
    fun `missing hidden and mismatched canvas reject atomically`() {
        val size = canvas(8, 8)
        val initial = state(size, List(64) { if (it == 0) redIndex else blackIndex })
        val gateway = CommandGateway.create(initial)
        val missing = value(LayerId.create(99))
        assertEquals(
            RejectionReason.LayerNotFound(missing),
            rejected(gateway.execute(command(gateway, transfer(size), missing))),
        )
        assertEquals(
            RejectionReason.CanvasMismatch(canvas(3, 1), size),
            rejected(gateway.execute(command(gateway, transfer(canvas(3, 1))))),
        )
        assertSame(initial, gateway.runtimeState.documentState)
        applied(
            gateway.execute(
                SetLayerVisibilityCommand.create(gateway.captureSource(), LayerId.first(), LayerVisibility.Hidden),
            ),
        )
        val hidden = gateway.runtimeState

        assertEquals(
            RejectionReason.LayerHidden(LayerId.first()),
            rejected(gateway.execute(command(gateway, transfer(size)))),
        )
        assertEquals(hidden, gateway.runtimeState)
    }

    @Test
    fun `revision exhaustion rejects the effective transfer without retained history`() {
        val size = canvas(3, 1)
        val initial =
            state(
                size,
                listOf(redIndex, blackIndex, blackIndex),
                identity = DocumentIdentity(revision = revision(Long.MAX_VALUE)),
            )
        val gateway = CommandGateway.create(initial)

        assertEquals(RejectionReason.RevisionOverflow, rejected(gateway.execute(command(gateway, transfer(size)))))
        assertSame(initial, gateway.runtimeState.documentState)
        assertEquals(0, gateway.runtimeState.historyEntryCount)
    }

    private fun command(
        gateway: CommandGateway,
        transfer: PixelTransfer,
        layerId: LayerId = LayerId.first(),
    ): TransferPixelsCommand = TransferPixelsCommand.create(gateway.captureSource(), layerId, transfer)

    private fun transfer(size: CanvasSize): PixelTransfer =
        value(
            PixelTransfer.create(
                size,
                value(PixelRegion.create(size, position(0, 0), canvas(1, 1))),
                PixelPlacement.create(position(1, 0)),
                PixelTransferMode.Move,
            ),
        )

    private fun <T> value(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Invalid transfer fixture: ${result.rejection}")
        }
}
