package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.editor.ActivePersistenceOperation
import io.github.hideyukimori.nenepixel.core.application.editor.LegacyImportPhase
import io.github.hideyukimori.nenepixel.core.application.editor.LegacyReductionStart
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.LegacyImportPlanner
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class LegacyReductionOwnerReplacementTest {
    @Test
    fun `destination replacement drops old bound planner preview snapshot and projection owners`() =
        runBlocking {
            val fixture = initializedFixture()
            fixture.storage.loadHandler = {
                ProjectLoadOutcome.Loaded(DocumentImportSource.Legacy(legacySource()))
            }
            val operation = (fixture.workflow.load() as PersistenceRequestResult.LegacyConversionRequired).operation
            val firstDestination = destination(colorOffset = 0x10000)
            val secondDestination = destination(colorOffset = 0x20000)
            val legacy = fixture.runtime.switchOperations.legacy

            val firstPermit = (legacy.beginReduction(operation, firstDestination) as LegacyReductionStart.Permit).permit
            val firstPreview = LegacyImportPlanner.reduce(firstPermit.candidate, firstDestination)
            legacy.completeReduction(firstPermit, firstPreview)
            val firstBound = fixture.runtime.currentBoundReduction()
            assertSame(firstPreview, firstBound.preview)
            assertSame(firstPreview.snapshot, firstBound.preview.snapshot)

            val secondPermit =
                (legacy.beginReduction(operation, secondDestination) as LegacyReductionStart.Permit).permit
            assertInstanceOf(LegacyImportPhase.Reducing::class.java, fixture.runtime.currentLegacyPhase())
            val secondPreview = LegacyImportPlanner.reduce(secondPermit.candidate, secondDestination)
            legacy.completeReduction(secondPermit, secondPreview)
            val secondBound = fixture.runtime.currentBoundReduction()

            assertSame(secondPreview, secondBound.preview)
            assertSame(secondPreview.snapshot, secondBound.preview.snapshot)
            assertNotSame(firstPreview, secondBound.preview)
            assertNotSame(firstPreview.snapshot, secondBound.preview.snapshot)
            assertNotSame(firstBound.projection, secondBound.projection)
        }

    private fun legacySource(): LegacyRgbaSource {
        val size =
            CanvasSize.create(
                CanvasWidth.create(256).requiredOwnerValue(),
                CanvasHeight.create(2).requiredOwnerValue(),
            )
        return LegacyRgbaSource
            .createPackedRgba8888(
                DocumentId.create("88888888888888888888888888888888").requiredOwnerValue(),
                Revision.create(41L).requiredOwnerValue(),
                size,
                IntArray(size.pixelCount.toInt()) { position -> position % 257 },
            ).requiredOwnerValue()
    }

    private fun destination(colorOffset: Int): PaletteDefinition =
        PaletteDefinition
            .create(
                Palette
                    .create(
                        List(256) { slot -> PixelColor.fromPackedRgba8888(colorOffset + slot) },
                    ).requiredOwnerValue(),
                PaletteIndex.first,
            ).requiredOwnerValue()
}

private fun io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime.currentLegacyPhase():
    LegacyImportPhase =
    read { transaction ->
        val operation = transaction.coordination.activeOperation as ActivePersistenceOperation.Switch.LegacyImport
        operation.phase
    }

private fun io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime.currentBoundReduction() =
    requireNotNull((currentLegacyPhase() as LegacyImportPhase.Required).preview)

private fun <T> DomainValueResult<T>.requiredOwnerValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Invalid legacy owner replacement fixture: $rejection")
    }
