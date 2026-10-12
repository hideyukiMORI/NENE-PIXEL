package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftOperation
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameDuration
import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues
import io.github.hideyukimori.nenepixel.presentation.compose.requiredValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class EditorFrameCallbacksTest {
    @Test
    fun `row commands retain stable targets order duration and undo redo`() {
        val controller = PresentationTestValues.fixture().controller
        val frames = controller.callbacks.frames
        val first = controller.renderState.activeFrameId
        frames.onAdd(first)
        val second = controller.renderState.activeFrameId
        frames.onSelect(first)
        frames.onDuration(second, FrameDuration.create(250).requiredValue())
        frames.onDuplicate(second)
        val duplicate = controller.renderState.activeFrameId
        val source = requireNotNull(controller.documentState.frameById(second))
        val copy = requireNotNull(controller.documentState.frameById(duplicate))
        assertEquals(source.duration, copy.duration)
        assertSame(source.layers.single().snapshot, copy.layers.single().snapshot)
        frames.onMove(duplicate, 0)
        val moved = controller.documentState
        assertEquals(listOf(duplicate, first, second), moved.frames.map { it.id })
        frames.onDelete(second)
        assertEquals(listOf(duplicate, first), controller.documentState.frames.map { it.id })
        controller.callbacks.onUndo()
        assertEquals(moved.frames, controller.documentState.frames)
        controller.callbacks.onRedo()
        assertEquals(listOf(duplicate, first), controller.documentState.frames.map { it.id })
        assertNull(controller.renderState.frameNotice)
    }

    @Test
    fun `palette draft survives frame focus and refuses focus during a colour gesture`() {
        val controller = PresentationTestValues.fixture().controller
        val first = controller.renderState.activeFrameId
        controller.callbacks.frames.onAdd(first)
        controller.callbacks.palette.onBegin()
        controller.callbacks.palette.onEdit(
            PaletteDraftOperation.SetSlotColor(PaletteIndex.first, PresentationTestValues.green),
        )
        val session = controller.renderState.paletteEditSession
        controller.callbacks.frames.onSelect(first)
        assertSame(session, controller.renderState.paletteEditSession)
        controller.callbacks.palette.colorEdit
            .begin(PaletteIndex.first)
        val other =
            controller.documentState.frames
                .last()
                .id
        val result = controller.callbacks.frames.onSelect(other)
        assertInstanceOf(WorkspaceReductionResult.Rejected::class.java, result)
        assertEquals(first, controller.renderState.activeFrameId)
        controller.callbacks.palette.colorEdit
            .cancel()
        controller.callbacks.frames.onSelect(other)
        assertEquals(other, controller.renderState.activeFrameId)
        assertNull(controller.renderState.frameNotice)
    }

    @Test
    fun `picked PNG remains targeted at its captured frame after focus changes`() {
        val fixture = PresentationTestValues.fixture()
        val first = fixture.controller.renderState.activeFrameId
        fixture.controller.callbacks.frames
            .onAdd(first)
        val second = fixture.controller.renderState.activeFrameId
        fixture.controller.callbacks.frames
            .onSelect(first)
        val raster = ImportRaster.create(1, 1, intArrayOf(0xFF0000FF.toInt())).requiredValue()
        pickPngImport(fixture, raster)
        val pending = requireNotNull(fixture.controller.renderState.pendingImport)
        assertEquals(first, pending.target.frameId)
        assertEquals(1, pending.target.frameOrdinal)
        assertEquals(2, pending.target.frameCount)
        fixture.controller.callbacks.frames
            .onSelect(second)
        val imported =
            fixture.controller.callbacks.rasterImport
                .onAppend()
        assertEquals(2, requireNotNull(imported.document.frameById(first)).layers.size)
        assertEquals(1, requireNotNull(imported.document.frameById(second)).layers.size)
        assertEquals(first, imported.activeFrameId)
        assertNull(imported.pendingImport)
        assertNull(imported.importNotice)
    }
}
