package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.persistence.Fixture
import io.github.hideyukimori.nenepixel.core.application.persistence.apply
import io.github.hideyukimori.nenepixel.core.application.persistence.importRaster
import io.github.hideyukimori.nenepixel.core.application.persistence.initializedFixture
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.NewWorkImportOption
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.PendingRasterImport
import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

/** ADR 0033: opening the pending new-work plan starts a switch, which then owns the plan. */
internal class RuntimeSwitchImportedWorkTest {
    @Test
    fun `without a pending import nothing starts`() =
        runBlocking {
            val fixture = initializedFixture()
            val operation = fixture.workflow.operation.value
            val workspace = fixture.runtime.state.workspaceState

            assertEquals(SwitchStart.NoPendingImport, fixture.runtime.switchOperations.beginImportedWork())

            assertEquals(operation, fixture.workflow.operation.value)
            assertSame(workspace, fixture.runtime.state.workspaceState)
        }

    @Test
    fun `an unavailable new-work form keeps the pending import`() =
        runBlocking {
            val fixture = initializedFixture()
            val pending = pending(wideRaster())
            assertSame(NewWorkImportOption.AboveCanvasLimit, pending.newWork)
            fixture.runtime.reduce(WorkspaceAction.SetPendingRasterImport(pending))

            assertEquals(SwitchStart.NoPendingImport, fixture.runtime.switchOperations.beginImportedWork())

            assertSame(pending, fixture.runtime.state.workspaceState.pendingImport)
        }

    @Test
    fun `a clean work switches at once and the switch takes the plan`() =
        runBlocking {
            val fixture = withPendingNewWork()

            val start = fixture.runtime.switchOperations.beginImportedWork()

            assertInstanceOf(SwitchStart.Ready::class.java, start)
            assertNull(fixture.runtime.state.workspaceState.pendingImport)
        }

    @Test
    fun `a dirty work asks first and the switch takes the plan`() =
        runBlocking {
            val fixture = withPendingNewWork()
            apply(fixture.runtime, position(1, 1), red)

            val start = fixture.runtime.switchOperations.beginImportedWork()

            assertInstanceOf(SwitchStart.Confirmation::class.java, start)
            assertNull(fixture.runtime.state.workspaceState.pendingImport)
        }

    @Test
    fun `a busy lease keeps the pending import`() =
        runBlocking {
            val fixture = withPendingNewWork()
            val pending = fixture.runtime.state.workspaceState.pendingImport
            assertInstanceOf(PngImportPickStart.Started::class.java, fixture.runtime.pngImportOperations.beginPick())

            assertEquals(SwitchStart.Busy, fixture.runtime.switchOperations.beginImportedWork())

            assertSame(pending, fixture.runtime.state.workspaceState.pendingImport)
        }

    private suspend fun withPendingNewWork(): Fixture {
        val fixture = initializedFixture()
        val pending = pending(importRaster())
        assertInstanceOf(NewWorkImportOption.Available::class.java, pending.newWork)
        fixture.runtime.reduce(WorkspaceAction.SetPendingRasterImport(pending))
        return fixture
    }

    private fun pending(raster: ImportRaster): PendingRasterImport =
        PendingRasterImport.planned(raster, canvas(4, 4), defaultDefinition)

    private fun wideRaster(): ImportRaster =
        when (val result = ImportRaster.create(WIDE, 1, IntArray(WIDE) { OPAQUE_RED })) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Wide raster rejected: ${result.rejection}")
        }

    private companion object {
        const val WIDE: Int = 257
        const val OPAQUE_RED: Int = 0xFF0000FF.toInt()
    }
}
