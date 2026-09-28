package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.blackIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.cellAt
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.snapshot
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.prepared
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.reduced
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.rejected
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.EyedropperState
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelection
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class ActiveLayerTargetTest {
    private val size = canvas(2, 1)
    private val reducer = WorkspaceReducer.create()
    private val topId = LayerId.create(2).value()

    @Test
    fun `edit target replaces one part at a time`() {
        val target = EditTarget.create(LayerId.first(), paletteIndex(0))

        assertEquals(EditTarget.create(topId, paletteIndex(0)), target.withLayer(topId))
        assertEquals(EditTarget.create(LayerId.first(), paletteIndex(2)), target.withPaletteIndex(paletteIndex(2)))
        assertEquals(LayerId.first(), WorkspaceState.create(size).activeLayerId)
        assertEquals(paletteIndex(0), WorkspaceState.create(size).activePaletteIndex)
    }

    @Test
    fun `reconcile document layer moves only the active layer`() {
        val gateway = CommandGateway.create(twoLayers())
        val initial = WorkspaceState.create(size)

        val next = reduced(reducer.reduce(initial, ReconcileDocumentLayer(topId), gateway.captureSource()))

        assertEquals(topId, next.activeLayerId)
        assertEquals(initial.activePaletteIndex, next.activePaletteIndex)
    }

    @Test
    fun `gesture keeps its captured layer after the selection changes and draws only there`() {
        val gateway = CommandGateway.create(twoLayers())
        val source = gateway.captureSource()
        val onTop = WorkspaceState.create(size).onLayer(topId)

        val begun = reduced(reducer.reduce(onTop, WorkspaceAction.BeginGesturePreview(size, position(0, 0)), source))
        assertEquals(topId, checkNotNull(begun.preview).layerId)
        val retargeted = begun.onLayer(LayerId.first())
        val extended = reduced(reducer.reduce(retargeted, WorkspaceAction.ExtendGesturePreview(position(1, 0)), source))
        val commit = prepared(reducer.reduce(extended, WorkspaceAction.PrepareGestureCommit, source))
        assertEquals(topId, commit.layerId)
        assertEquals(StrokeEffect.Paint(blackIndex), commit.stroke.effect)

        applied(gateway.execute(ApplyStrokeCommand.create(commit.admission, commit.layerId, commit.stroke)))

        val layers = gateway.runtimeState.documentState.layers
        assertEquals(twoLayers().layers.first(), layers.first())
        assertEquals(PixelCell.Covered(blackIndex), cellAt(layers.last().snapshot, position(0, 0)))
        assertEquals(PixelCell.Covered(blackIndex), cellAt(layers.last().snapshot, position(1, 0)))
    }

    @Test
    fun `stroke changes only the commanded layer`() {
        val gateway = CommandGateway.create(twoLayers())
        val command = ApplyStrokeCommand.create(gateway.captureSource(), topId, redStroke(1))

        applied(gateway.execute(command))

        val layers = gateway.runtimeState.documentState.layers
        assertEquals(twoLayers().layers.first(), layers.first())
        assertEquals(PixelCell.Empty, cellAt(layers.last().snapshot, position(0, 0)))
        assertEquals(PixelCell.Covered(redIndex), cellAt(layers.last().snapshot, position(1, 0)))
    }

    @Test
    fun `stroke on a missing layer is rejected`() {
        val gateway = CommandGateway.create(twoLayers())
        val missing = LayerId.create(3).value()
        val before = gateway.runtimeState.documentState

        val command = ApplyStrokeCommand.create(gateway.captureSource(), missing, redStroke(0))

        val reason = CommandResultAssertions.rejected(gateway.execute(command))

        assertEquals(RejectionReason.LayerNotFound(missing), reason)
        assertEquals(before, gateway.runtimeState.documentState)
    }

    @Test
    fun `eyedropper picks the covered cell of the active top layer`() {
        val gateway = CommandGateway.create(twoLayers(top = snapshot(size, listOf(redIndex, redIndex))))

        val picked = reduced(pick(armedOn(topId), gateway, 0))

        assertEquals(redIndex, picked.activePaletteIndex)
        assertEquals(EyedropperState.Idle, picked.quickSelection.eyedropper)
    }

    @Test
    fun `eyedropper on an empty cell is rejected without changing the selection`() {
        val gateway = CommandGateway.create(twoLayers())
        val armed = armedOn(topId)

        val result = rejected(pick(armed, gateway, 1))

        assertEquals(WorkspaceActionRejection.PickEmptyCell(position(1, 0)), result.rejection)
        assertSame(armed, result.nextState)
        assertEquals(EyedropperState.Armed, result.nextState.quickSelection.eyedropper)
    }

    @Test
    fun `eyedropper on a missing active layer is rejected`() {
        val gateway = CommandGateway.create(state(size))
        val armed = armedOn(topId)

        val result = rejected(pick(armed, gateway, 0))

        assertEquals(WorkspaceActionRejection.ActiveLayerNotFound(topId), result.rejection)
        assertSame(armed, result.nextState)
    }

    private fun pick(
        state: WorkspaceState,
        gateway: CommandGateway,
        x: Int,
    ): WorkspaceReductionResult =
        reducer.reduce(state, WorkspaceAction.PickPaletteEntryAt(position(x, 0)), gateway.captureSource())

    private fun redStroke(x: Int) = stroke(size, listOf(position(x, 0)), redIndex)

    private fun armedOn(layerId: LayerId): WorkspaceState =
        WorkspaceState
            .create(size)
            .onLayer(layerId)
            .withQuickSelection(QuickSelection.initial.armed())

    private fun WorkspaceState.onLayer(layerId: LayerId): WorkspaceState = withEditTarget(editTarget.withLayer(layerId))

    private fun twoLayers(top: PixelSnapshot = PixelSnapshot.createEmpty(size)): DocumentState =
        DocumentState
            .createLayered(
                state(size).id,
                state(size).revision,
                defaultDefinition,
                listOf(
                    Layer.create(LayerId.first(), LayerName.empty, LayerVisibility.Visible, snapshot(size)),
                    Layer.create(topId, LayerName.empty, LayerVisibility.Visible, top),
                ),
            ).value()

    private fun <T> DomainValueResult<T>.value(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> error("Expected a created value but was $rejection")
        }
}
