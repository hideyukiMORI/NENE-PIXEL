package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.rejected
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.duration
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.frame
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.id
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.required
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layer
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class FrameCommandTest {
    @Test fun `blank frame add uses global fresh IDs defaultduration full Emptycharge and exact undo redo`() {
        val original = state(frame(1, listOf(layer(1, "a"))), frame(3, listOf(layer(8, "b"))))

        val gateway = CommandGateway.create(original)

        val change = applied(gateway.execute(AddFrameCommand.create(gateway.captureSource(), id(1))))

        val added = gateway.runtimeState.documentState

        assertEquals(listOf(1, 4, 3), added.frames.map { it.id.value })

        assertEquals(100, added.frames[1].duration.value)

        assertEquals(
            9,
            added.frames[1]
                .layers
                .single()
                .id.value,
        )

        assertTrue(
            added.frames[1]
                .layers
                .single()
                .snapshot
                .copyCoverage()
                .all { it == 0.toByte() },
        )

        assertEquals(53L, change.retainedByteCount)

        assertEquals(change.retainedByteCount, change.inverse().retainedByteCount)

        assertEquals(1, gateway.runtimeState.historyEntryCount)

        applied(gateway.execute(UndoCommand.create(added.id, added.revision)))

        assertEquals(original, gateway.runtimeState.documentState)

        applied(gateway.execute(RedoCommand.create(original.id, original.revision)))

        assertEquals(added, gateway.runtimeState.documentState)
    }

    @Test fun `duplicate preserves exact layer metadata and shared immutable pixels then edits independently`() {
        val source =
            frame(1, listOf(layer(1, "\u7d75"), layer(2, "top", visibility = LayerVisibility.Hidden)), duration(250))

        val original = state(source, frame(3, listOf(layer(8, "b"))))

        val gateway = CommandGateway.create(original)

        val change = applied(gateway.execute(DuplicateFrameCommand.create(gateway.captureSource(), id(1))))

        val duplicate = gateway.runtimeState.documentState.frames[1]

        assertEquals(listOf(9, 10), duplicate.layers.map { it.id.value })

        assertEquals(source.duration, duplicate.duration)

        assertEquals(source.layers.map { it.name }, duplicate.layers.map { it.name })

        assertEquals(source.layers.map { it.visibility }, duplicate.layers.map { it.visibility })

        source.layers.indices.forEach { assertSame(source.layers[it].snapshot, duplicate.layers[it].snapshot) }

        assertEquals(68L, change.retainedByteCount)

        assertEquals(change.retainedByteCount, change.inverse().retainedByteCount)

        applied(
            gateway.execute(
                ApplyStrokeCommand.create(
                    gateway.captureSource(),
                    layerId(9),
                    stroke(original.size, listOf(position(0, 0)), redIndex),
                ),
            ),
        )

        assertSame(
            source,
            gateway.runtimeState.documentState.frames
                .first(),
        )

        assertNotEquals(
            source.layers.first().snapshot,
            gateway.runtimeState.documentState.frames[1]
                .layers
                .first()
                .snapshot,
        )
    }

    @Test fun `move and duration keep exact metadata inverse replay and fixed logical charge`() {
        val original = state(frame(1, listOf(layer(1, "a"))), frame(2, listOf(layer(2, "b"))))

        val gateway = CommandGateway.create(original)

        val moved = applied(gateway.execute(MoveFrameCommand.create(gateway.captureSource(), id(1), 1)))

        val timed =
            applied(gateway.execute(SetFrameDurationCommand.create(gateway.captureSource(), id(1), duration(60000))))

        val final = gateway.runtimeState.documentState

        assertEquals(listOf(2, 1), final.frames.map { it.id.value })

        assertEquals(
            60000,
            final.frames
                .last()
                .duration.value,
        )

        assertEquals(44L, moved.retainedByteCount)

        assertEquals(44L, timed.retainedByteCount)

        assertEquals(88L, gateway.runtimeState.retainedHistoryByteCount)

        repeat(2) {
            val current = gateway.runtimeState.documentState
            applied(gateway.execute(UndoCommand.create(current.id, current.revision)))
        }

        assertEquals(original, gateway.runtimeState.documentState)

        repeat(2) {
            val current = gateway.runtimeState.documentState
            applied(gateway.execute(RedoCommand.create(current.id, current.revision)))
        }

        assertEquals(final, gateway.runtimeState.documentState)
    }

    @Test fun `no-op edits preserve redo position and stale admission rejects unchanged`() {
        val original = state(frame(1, listOf(layer(1, "a"))))

        val gateway = CommandGateway.create(original)

        val stale = AddFrameCommand.create(gateway.captureSource(), id(1))

        applied(gateway.execute(stale))

        val added = gateway.runtimeState.documentState

        assertEquals(RejectionReason.SourceHistoryMismatch, rejected(gateway.execute(stale)))

        applied(gateway.execute(UndoCommand.create(added.id, added.revision)))

        val before = gateway.runtimeState

        assertEquals(
            RejectionReason.NoEffectiveChange,
            rejected(gateway.execute(MoveFrameCommand.create(gateway.captureSource(), id(1), 0))),
        )

        assertEquals(
            RejectionReason.NoEffectiveChange,
            rejected(gateway.execute(SetFrameDurationCommand.create(gateway.captureSource(), id(1), duration(100)))),
        )

        assertEquals(before, gateway.runtimeState)

        applied(gateway.execute(RedoCommand.create(original.id, original.revision)))

        assertEquals(added, gateway.runtimeState.documentState)
    }

    @Test fun `global budget lastframe invalidposition and missingframe reject atomically`() {
        val full = state(frame(1, (1..16).map { layer(it, "") }))

        val gateway = CommandGateway.create(full)

        assertEquals(
            RejectionReason.LayerLimitReached,
            rejected(gateway.execute(AddFrameCommand.create(gateway.captureSource(), id(1)))),
        )

        assertEquals(
            RejectionReason.LayerLimitReached,
            rejected(gateway.execute(DuplicateFrameCommand.create(gateway.captureSource(), id(1)))),
        )

        assertEquals(
            RejectionReason.LastFrameNotDeletable,
            rejected(gateway.execute(DeleteFrameCommand.create(gateway.captureSource(), id(1)))),
        )

        assertEquals(
            RejectionReason.FramePositionOutOfRange(1, 1),
            rejected(gateway.execute(MoveFrameCommand.create(gateway.captureSource(), id(1), 1))),
        )

        assertEquals(
            RejectionReason.FrameNotFound(id(99)),
            rejected(gateway.execute(DeleteFrameCommand.create(gateway.captureSource(), id(99)))),
        )

        val sixteenFrames =
            CommandGateway.create(
                state(*(1..16).map { frame(it, listOf(layer(it, ""))) }.toTypedArray()),
            )
        assertEquals(
            RejectionReason.FrameLimitReached,
            rejected(sixteenFrames.execute(AddFrameCommand.create(sixteenFrames.captureSource(), id(1)))),
        )
        assertSame(full, gateway.runtimeState.documentState)

        assertEquals(0, gateway.runtimeState.historyEntryCount)
    }

    @Test fun `frame and bulk layer ID overflow and revision overflow reject unchanged`() {
        val frameOverflow = CommandGateway.create(state(frame(Int.MAX_VALUE, listOf(layer(1, "")))))

        assertEquals(
            RejectionReason.FrameIdOverflow,
            rejected(frameOverflow.execute(AddFrameCommand.create(frameOverflow.captureSource(), id(Int.MAX_VALUE)))),
        )

        val layerOverflow = CommandGateway.create(state(frame(1, listOf(layer(1, ""), layer(Int.MAX_VALUE - 1, "")))))

        assertEquals(
            RejectionReason.LayerIdOverflow,
            rejected(layerOverflow.execute(DuplicateFrameCommand.create(layerOverflow.captureSource(), id(1)))),
        )

        assertEquals(0, layerOverflow.runtimeState.historyEntryCount)
        assertEquals(
            listOf(1, Int.MAX_VALUE - 1),
            layerOverflow.runtimeState.documentState.frames.single().layers.map {
                it.id.value
            },
        )
        val ordinary = state(frame(1, listOf(layer(1, ""))))

        val exhausted =
            required(DocumentState.create(ordinary.id, revision(Long.MAX_VALUE), ordinary.definition, ordinary.frames))

        val gateway = CommandGateway.create(exhausted)

        assertEquals(
            RejectionReason.RevisionOverflow,
            rejected(gateway.execute(AddFrameCommand.create(gateway.captureSource(), id(1)))),
        )

        assertSame(exhausted, gateway.runtimeState.documentState)

        assertEquals(0, gateway.runtimeState.historyEntryCount)
    }
}
