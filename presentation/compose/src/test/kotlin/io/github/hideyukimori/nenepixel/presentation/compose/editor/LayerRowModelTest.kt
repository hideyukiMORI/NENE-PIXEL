package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.AddLayerCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.DocumentCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.RenameLayerCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.SetLayerVisibilityCommand
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfacePoint
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.presentation.compose.EditorFixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import io.github.hideyukimori.nenepixel.presentation.compose.requiredValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class LayerRowModelTest {
    @Test
    fun `rows list the front-most layer first`() {
        val fixture = fixture()
        val bottom = LayerId.first()
        execute(fixture, AddLayerCommand.create(fixture.runtime.captureSource(), bottom))
        val middle = layerId(2)
        execute(fixture, AddLayerCommand.create(fixture.runtime.captureSource(), middle))

        val rows = layerRowsOf(fixture.runtime.state.documentState)

        assertEquals(listOf(layerId(3), middle, bottom), rows.map(LayerRowModel::id))
    }

    @Test
    fun `an unnamed layer keeps its empty name and a named layer keeps its name`() {
        val fixture = fixture()
        execute(fixture, AddLayerCommand.create(fixture.runtime.captureSource(), LayerId.first()))
        val name = LayerName.create("Line").requiredValue()
        execute(fixture, RenameLayerCommand.create(fixture.runtime.captureSource(), LayerId.first(), name))

        val rows = layerRowsOf(fixture.runtime.state.documentState)

        assertEquals(listOf(LayerName.empty, name), rows.map(LayerRowModel::name))
    }

    @Test
    fun `a hidden layer maps to a hidden row`() {
        val fixture = fixture()
        val hidden = LayerVisibility.Hidden
        execute(fixture, SetLayerVisibilityCommand.create(fixture.runtime.captureSource(), LayerId.first(), hidden))

        val rows = layerRowsOf(fixture.runtime.state.documentState)

        assertEquals(listOf(LayerRowModel(LayerId.first(), LayerName.empty, hidden)), rows)
    }

    @Test
    fun `committing a stroke leaves the rows structurally equal`() {
        val fixture = fixture()
        val before = fixture.runtime.state.documentState
        val surface = ViewportSurface.create(SURFACE_EDGE, SURFACE_EDGE, PIXELS_PER_DP).requiredValue()
        val point = ViewportSurfacePoint.create(HALF_CELL, HALF_CELL).requiredValue()
        fixture.controller.pointerDown(surface, point)

        val end = fixture.controller.pointerEnd(surface, point)

        assertInstanceOf(PointerInputAcknowledgement.Accepted::class.java, end)
        val after = fixture.runtime.state.documentState
        assertNotEquals(before.layers, after.layers)
        assertEquals(layerRowsOf(before), layerRowsOf(after))
    }

    private fun execute(
        fixture: EditorFixture,
        command: DocumentCommand,
    ) {
        assertInstanceOf(CommandResult.Applied::class.java, fixture.runtime.execute(command))
    }

    private fun layerId(value: Int): LayerId = LayerId.create(value).requiredValue()

    private fun <T> ViewportValueResult<T>.requiredValue(): T =
        when (this) {
            is ViewportValueResult.Created -> value
            is ViewportValueResult.Rejected -> fail("Viewport test value was rejected: $rejection")
        }

    private companion object {
        const val SURFACE_EDGE: Int = 400
        const val PIXELS_PER_DP: Double = 2.0
        const val HALF_CELL: Double = 50.0
    }
}
