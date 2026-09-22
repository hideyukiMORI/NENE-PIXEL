package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.blackIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.definition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentDirtyState
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class LegacyImportWorkflowTest {
    @Test
    fun `lossless legacy user load preserves identity and revision and installs a clean empty history`() =
        runBlocking {
            val fixture = initializedFixture()
            val source = legacySource(256)
            fixture.storage.loadHandler = { ProjectLoadOutcome.Loaded(DocumentImportSource.Legacy(source)) }

            assertCompleted(PersistenceLastOutcome.Loaded, fixture.workflow.load())

            val installed = fixture.runtime.state
            assertEquals(source.id, installed.documentState.id)
            assertEquals(source.revision, installed.documentState.revision)
            assertEquals(DocumentDirtyState.Clean, installed.dirtyState)
            assertEquals(HistoryAvailability.None, installed.historyAvailability)
            assertEquals(256, installed.documentState.definition.palette.entryCount)
        }

    @Test
    fun `lossless legacy recovery remains unadopted until acceptance and installs dirty`() =
        runBlocking {
            val source = legacySource(256)
            val fixture = Fixture(RecoveryInspection.Candidate(generation(6), DocumentImportSource.Legacy(source)))

            fixture.initialize()
            assertSame(RecoveryStatus.UnadoptedCandidate, fixture.workflow.operation.value.recoveryStatus)
            assertCompleted(PersistenceLastOutcome.Recovered, fixture.workflow.acceptRecovery())
            assertEquals(source.id, fixture.runtime.state.documentState.id)
            assertEquals(source.revision, fixture.runtime.state.documentState.revision)
            assertEquals(DocumentDirtyState.Dirty, fixture.runtime.state.dirtyState)
        }

    @Test
    fun `user v1 source remains uninstalled until explicit reduction then installs fresh dirty owners`() =
        runBlocking {
            val fixture = initializedFixture()
            val source = legacySource(257)
            fixture.storage.loadHandler = { ProjectLoadOutcome.Loaded(DocumentImportSource.Legacy(source)) }

            val result = fixture.workflow.load()
            val operation =
                assertInstanceOf(
                    PersistenceRequestResult.LegacyConversionRequired::class.java,
                    result,
                ).operation
            val required = assertLegacyRequired(fixture)
            assertEquals(257, required.distinctColorCount)
            assertEquals(LegacyImportOrigin.USER_FILE, required.origin)
            assertEquals(LegacyOriginalCopyStatus.OPTIONAL, required.originalCopy)
            assertEquals(
                LegacyPreviewColorResult.Color(PixelColor.fromPackedRgba8888(0xff)),
                required.source.colorAt(position(0, 0)),
            )

            val first = assertReady(fixture.workflow.legacyImport.previewSource(operation, destination(red, green)))
            val second = assertReady(fixture.workflow.legacyImport.previewSource(operation, destination(green, red)))
            val third = assertReady(fixture.workflow.legacyImport.previewSource(operation, destination(red, green)))
            assertNotEquals(first, second)
            assertNotEquals(first, third)
            val finalProjection = assertLegacyRequired(fixture)
            assertEquals(destination(red, green), finalProjection.selectedDestination)
            assertEquals(third, requireNotNull(finalProjection.reduction).handle)

            assertCompleted(
                PersistenceLastOutcome.LegacyConverted,
                fixture.workflow.legacyImport.acceptReduction(third),
            )
            val installed = fixture.runtime.state
            assertNotEquals(source.id, installed.documentState.id)
            assertEquals(Revision.initial(), installed.documentState.revision)
            assertEquals(DocumentDirtyState.Dirty, installed.dirtyState)
            assertEquals(HistoryAvailability.None, installed.historyAvailability)
            assertEquals(destination(red, green), installed.documentState.definition)
        }

    @Test
    fun `legacy acceptance recognizes an edit undone to the exact starting history position`() =
        runBlocking {
            val fixture = initializedFixture()
            fixture.storage.loadHandler = {
                ProjectLoadOutcome.Loaded(DocumentImportSource.Legacy(legacySource(257)))
            }
            val operation =
                assertInstanceOf(
                    PersistenceRequestResult.LegacyConversionRequired::class.java,
                    fixture.workflow.load(),
                ).operation
            val reduction = assertReady(fixture.workflow.legacyImport.previewSource(operation, destination(red, green)))

            apply(fixture.runtime, position(0, 0), red)
            undo(fixture.runtime)

            assertCompleted(
                PersistenceLastOutcome.LegacyConverted,
                fixture.workflow.legacyImport.acceptReduction(reduction),
            )
        }

    @Test
    fun `legacy acceptance refreshes source consent and retains both previews in confirmation`() =
        runBlocking {
            val fixture = initializedFixture()
            fixture.storage.loadHandler = {
                ProjectLoadOutcome.Loaded(DocumentImportSource.Legacy(legacySource(257)))
            }
            val operation =
                assertInstanceOf(
                    PersistenceRequestResult.LegacyConversionRequired::class.java,
                    fixture.workflow.load(),
                ).operation
            val reduction = assertReady(fixture.workflow.legacyImport.previewSource(operation, destination(red, green)))
            apply(fixture.runtime, position(0, 0), red)

            val confirmation = assertAwaiting(fixture.workflow.legacyImport.acceptReduction(reduction))
            val phase =
                assertInstanceOf(
                    PersistenceOperationPhase.NeedsLegacyConfirmation::class.java,
                    fixture.workflow.operation.value.phase,
                )
            assertSame(confirmation, phase.request)
            assertEquals(reduction, requireNotNull(phase.import.reduction).handle)
            assertEquals(destination(red, green), phase.import.selectedDestination)

            assertCompleted(PersistenceLastOutcome.LegacyConverted, fixture.workflow.confirm(confirmation))
        }

    @Test
    fun `recovery reduction requires verified exact copy and keeps proof across a later copy failure`() =
        runBlocking {
            val source = legacySource(257)
            val fixture = Fixture(RecoveryInspection.Candidate(generation(7), DocumentImportSource.Legacy(source)))
            fixture.initialize()
            assertInstanceOf(
                RecoveryStatus.UnadoptedLegacyCandidate::class.java,
                fixture.workflow.operation.value.recoveryStatus,
            )

            val operation =
                assertInstanceOf(
                    PersistenceRequestResult.LegacyConversionRequired::class.java,
                    fixture.workflow.acceptRecovery(),
                ).operation
            val preview = assertReady(fixture.workflow.legacyImport.previewSource(operation, destination(red, green)))
            assertSame(
                PersistenceRequestResult.OriginalCopyRequired,
                fixture.workflow.legacyImport.acceptReduction(preview),
            )

            fixture.storage.copyHandler = { LegacySourceCopyOutcome.Copied }
            assertSame(LegacySourceCopyRequestResult.Copied, fixture.workflow.legacyImport.copySource(operation))
            failCopyAndAssertRetainedProof(fixture, operation)

            assertCompleted(
                PersistenceLastOutcome.LegacyConverted,
                fixture.workflow.legacyImport.acceptReduction(preview),
            )
            assertEquals(listOf(ExpectedRecoveryLineage.Present(generation(7))), fixture.recovery.retireCalls)
        }

    @Test
    fun `recovery-only decline cannot retire before the same operation verifies an original copy`() =
        runBlocking {
            val source = legacySource(257)
            val fixture = Fixture(RecoveryInspection.Candidate(generation(9), DocumentImportSource.Legacy(source)))
            fixture.initialize()

            val operation =
                assertInstanceOf(
                    PersistenceRequestResult.LegacyConversionRequired::class.java,
                    fixture.workflow.declineRecovery(),
                ).operation
            assertSame(
                PersistenceRequestResult.OriginalCopyRequired,
                fixture.workflow.legacyImport.declineRecovery(operation),
            )
            assertTrue(fixture.recovery.retireCalls.isEmpty())

            fixture.storage.copyHandler = { LegacySourceCopyOutcome.Copied }
            fixture.workflow.legacyImport.copySource(operation)
            assertCompleted(
                PersistenceLastOutcome.RecoveryDeclined,
                fixture.workflow.legacyImport.declineRecovery(operation),
            )
            assertEquals(listOf(ExpectedRecoveryLineage.Present(generation(9))), fixture.recovery.retireCalls)
        }

    @Test
    fun `verified original copy proof cannot cross operation identity`() =
        runBlocking {
            val source = legacySource(257)
            val fixture = Fixture(RecoveryInspection.Candidate(generation(11), DocumentImportSource.Legacy(source)))
            fixture.initialize()
            val first =
                assertInstanceOf(
                    PersistenceRequestResult.LegacyConversionRequired::class.java,
                    fixture.workflow.acceptRecovery(),
                ).operation
            fixture.storage.copyHandler = { LegacySourceCopyOutcome.Copied }
            assertSame(LegacySourceCopyRequestResult.Copied, fixture.workflow.legacyImport.copySource(first))
            assertSame(PersistenceCancellationResult.Cancelled, fixture.workflow.cancel(first))

            val second =
                assertInstanceOf(
                    PersistenceRequestResult.LegacyConversionRequired::class.java,
                    fixture.workflow.acceptRecovery(),
                ).operation
            val reduction = assertReady(fixture.workflow.legacyImport.previewSource(second, destination(red, green)))

            assertSame(
                PersistenceRequestResult.OriginalCopyRequired,
                fixture.workflow.legacyImport.acceptReduction(reduction),
            )
            assertTrue(fixture.recovery.retireCalls.isEmpty())
        }

    @Test
    fun `save as preserves an unadopted legacy recovery source`() =
        runBlocking {
            val source = legacySource(257)
            val fixture = Fixture(RecoveryInspection.Candidate(generation(13), DocumentImportSource.Legacy(source)))
            fixture.initialize()
            fixture.storage.saveHandler = { ProjectSaveOutcome.Saved }

            val saved = assertSaved(fixture.workflow.saveAs())

            assertSame(RecoveryCleanupOutcome.PreservedUnadoptedCandidate, saved.recoveryCleanup)
            assertTrue(fixture.recovery.retireCalls.isEmpty())
            val retained =
                assertInstanceOf(
                    RecoveryStatus.UnadoptedLegacyCandidate::class.java,
                    fixture.workflow.operation.value.recoveryStatus,
                )
            assertEquals(source.size, retained.source.size)
        }

    @Test
    fun `generic new and load replacements cannot bypass an unpreserved legacy recovery source`() =
        runBlocking {
            assertGenericReplacementBlocked { it.workflow.createNewDocument(newRequest(2, 2)) }
            assertGenericReplacementBlocked { it.workflow.load() }
        }

    @Test
    fun `copy cancellation retains the recovery source until the physical call drains`() =
        runBlocking {
            val source = legacySource(257)
            val fixture = Fixture(RecoveryInspection.Candidate(generation(3), DocumentImportSource.Legacy(source)))
            fixture.initialize()
            val operation =
                assertInstanceOf(
                    PersistenceRequestResult.LegacyConversionRequired::class.java,
                    fixture.workflow.acceptRecovery(),
                ).operation
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            fixture.storage.copyHandler = {
                entered.complete(Unit)
                release.await()
                LegacySourceCopyOutcome.Copied
            }
            val copy = async { fixture.workflow.legacyImport.copySource(operation) }
            entered.await()

            assertSame(PersistenceCancellationResult.CancellationStarted, fixture.workflow.cancel(operation))
            val cancelling =
                assertInstanceOf(
                    PersistenceOperationPhase.CancellingLegacyImport::class.java,
                    fixture.workflow.operation.value.phase,
                )
            assertEquals(operation, cancelling.import.operation)
            assertSame(LegacyCopyAttemptOutcome.NotAttempted, cancelling.import.latestCopyOutcome)
            assertEquals(null, cancelling.import.selectedDestination)
            assertInstanceOf(
                RecoveryStatus.UnadoptedLegacyCandidate::class.java,
                fixture.workflow.operation.value.recoveryStatus,
            )
            release.complete(Unit)
            assertSame(LegacySourceCopyRequestResult.Cancelled, copy.await())
            assertInstanceOf(
                RecoveryStatus.UnadoptedLegacyCandidate::class.java,
                fixture.workflow.operation.value.recoveryStatus,
            )
            assertSame(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
        }

    private fun assertLegacyRequired(fixture: Fixture): LegacyImportProjection =
        assertInstanceOf(
            PersistenceOperationPhase.LegacyConversionRequired::class.java,
            fixture.workflow.operation.value.phase,
        ).import

    private fun assertReady(result: LegacyReductionRequestResult): LegacyReductionHandle =
        assertInstanceOf(LegacyReductionRequestResult.Ready::class.java, result).handle

    private suspend fun assertGenericReplacementBlocked(request: suspend (Fixture) -> PersistenceRequestResult) {
        val source = legacySource(257)
        val fixture = Fixture(RecoveryInspection.Candidate(generation(15), DocumentImportSource.Legacy(source)))
        fixture.initialize()

        assertSame(PersistenceRequestResult.RecoveryUnavailable, request(fixture))
        assertEquals(0, fixture.storage.loadCalls)
        assertTrue(fixture.recovery.retireCalls.isEmpty())
        val retained =
            assertInstanceOf(
                RecoveryStatus.UnadoptedLegacyCandidate::class.java,
                fixture.workflow.operation.value.recoveryStatus,
            )
        assertEquals(source.size, retained.source.size)
    }

    private suspend fun failCopyAndAssertRetainedProof(
        fixture: Fixture,
        operation: PersistenceOperationHandle,
    ) {
        fixture.storage.copyHandler = {
            LegacySourceCopyOutcome.Failed(
                ProjectStorageFailure.ReadBackMismatch,
                PartialOutputCleanup.DELETED,
            )
        }
        assertInstanceOf(
            LegacySourceCopyRequestResult.Failed::class.java,
            fixture.workflow.legacyImport.copySource(operation),
        )
        val retained = assertLegacyRequired(fixture)
        assertEquals(LegacyOriginalCopyStatus.VERIFIED, retained.originalCopy)
        assertEquals(
            LegacyCopyAttemptOutcome.Failed(
                ProjectStorageFailure.ReadBackMismatch,
                PartialOutputCleanup.DELETED,
            ),
            retained.latestCopyOutcome,
        )
    }

    private fun destination(
        first: PixelColor,
        second: PixelColor,
    ): PaletteDefinition = definition(blackIndex, black, first, second)

    private fun legacySource(distinctColors: Int): LegacyRgbaSource {
        val size = canvas(256, 2)
        val pixels = IntArray(size.pixelCount.toInt()) { index -> (index % distinctColors shl 8) or 0xff }
        return created(
            LegacyRgbaSource.createPackedRgba8888(
                id = created(DocumentId.create("f".repeat(32))),
                revision = created(Revision.create(41L)),
                size = size,
                packedRgba8888 = pixels,
            ),
        )
    }

    private fun <T> created(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid legacy workflow fixture: ${result.rejection}")
        }
}
