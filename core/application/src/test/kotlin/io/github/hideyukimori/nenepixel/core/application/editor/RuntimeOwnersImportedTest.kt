package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.SequentialDocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.persistence.documentId
import io.github.hideyukimori.nenepixel.core.application.persistence.importRaster
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.importing.NewWorkImportPlan
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.NewWorkImportResult
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.RasterImportPlanner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

/** ADR 0033: a new work opened from a PNG is an unsaved single-layer document built from its plan. */
internal class RuntimeOwnersImportedTest {
    @Test
    fun `imported owners hold a new unsaved single-layer document of the plan`() {
        val plan = plan()
        val ids = SequentialDocumentIdSource()

        val owners = RuntimeOwners.createImported(plan, ids)

        val document = owners.commandGateway.runtimeState.documentState
        assertEquals(1, ids.callCount)
        assertEquals(documentId('1'), owners.documentId())
        assertEquals(Revision.initial(), document.revision)
        assertEquals(plan.definition, document.definition)
        assertEquals(1, document.layers.size)
        assertEquals(plan.snapshot, document.layers.single().snapshot)
        assertTrue(owners.isDirty())
    }

    private fun plan(): NewWorkImportPlan =
        when (val result = RasterImportPlanner.newWork(importRaster())) {
            is NewWorkImportResult.Planned -> result.plan
            else -> fail("New-work fixture was not planned: $result")
        }
}
